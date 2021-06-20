(ns decide.features.notifications.notifier
  (:require
    [chime.core :as chime]
    [clojure.core.async :as async]
    [clojure.set :as set]
    [datahike.api :as d]
    [decide.features.notifications.collect :as collect]
    [decide.features.notifications.format-email :as format]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]
    [decide.server-components.database :refer [conn]]
    [decide.server-components.email :refer [mailer-chan]]
    [mount.core :refer [defstate]]
    [taoensso.timbre :as log])
  (:import (java.time Instant Duration)))

(defn get-all-users-with-emails [db]
  (d/q '[:find [(pull ?user [::user/email ::user/display-name
                             {::process/_participants [:db/id ::process/slug]}]) ...]
         :where
         [?user ::user/email]
         [?process ::process/participants ?user]]
    db))


(defn- events->arg-tree [m {:event/keys [arg-path]}]
  ;; TODO refactor to tail recursion
  (let [[current & r] arg-path]
    (if-not r
      (update m current
        assoc
        :db/id current
        :new? true)
      (update m current
        #(-> %
           (assoc :db/id current)
           (update :arguments events->arg-tree {:event/arg-path r}))))))

(comment
  (events->arg-tree {} {:event/arg-path [1 2 3]})
  (events->arg-tree {} {:event/arg-path [1 2]})

  (events->arg-tree {} {:event/arg-path [1]}))

(defn- fill-argument-tree [db arg-tree]
  (let [arguments (vals arg-tree)]
    (for [argument arguments]
      (-> argument
        (merge (d/pull db [:argument/id
                           :argument/type
                           {:argument/premise [:statement/id :statement/content]}]
                 (:db/id argument)))
        (assoc :arguments (fill-argument-tree db (:arguments argument)))))))

(defn- build-argumentation-payload [db argument-events]
  (->> argument-events
    (reduce events->arg-tree {})
    (fill-argument-tree db)))


(defn- build-proposal-payload [db proposal argument-events]
  {:proposal proposal
   :arguments (build-argumentation-payload db argument-events)})

(defn- build-process-payload [db [process-eid events]]
  (let [{proposal-events :event.type/new-proposal
         argument-events :event.type/new-argument}
        (group-by :event/what events)

        new-proposal-eids (set (map :event/eid proposal-events))
        argument-proposal-eids (set (map :event/proposal argument-events))

        old-proposal-eids (set/difference argument-proposal-eids new-proposal-eids)

        arguments (group-by :event/proposal argument-events)



        new-proposals
        (->> (set/union new-proposal-eids old-proposal-eids)
          (d/pull-many db [:db/id ::proposal/id ::proposal/title])
          (map #(cond-> %
                  (contains? new-proposal-eids (:db/id %)) (assoc :new? true))))]


    (assoc
      (d/pull db [::process/slug ::process/title] process-eid)
      :proposals
      (concat
        (for [proposal new-proposals
              :let [eid (:db/id proposal)]]
          (build-proposal-payload db proposal (get arguments eid [])))))))

(defn- build-payload [db grouped-events user]
  {:user user
   :base-url "https://decide.ebbinghaus.me"                 ; FIXME Get this from the config
   :processes
   (map #(build-process-payload db %) grouped-events)})

(defn- build-mail [db user events]
  (let [process-ids (map :db/id (get user ::process/_participants []))
        grouped-events (select-keys (group-by :event/process events) process-ids)]
    #_(format/make-message)
    (build-payload db grouped-events user)))

(defn notify-user! [db user events]
  (let [process-ids (map :db/id (get user ::process/_participants []))
        grouped-events (select-keys (group-by :event/process events) process-ids)]
    (when-not (empty? grouped-events)
      (log/debug "Notify " (::user/email user))
      (async/put! mailer-chan
        (format/make-message
          (build-payload db grouped-events user))))))

(comment

  (require '[decide.server-components.database :refer [conn]])

  (let [db @conn]
    (build-mail db {::user/email "ebbinghaus@hhu.de" ::user/display-name "Björn"
                    ::process/_participants [{:db/id 202}]}
      (collect/all-events db #inst"2021-05-01")))

  (collect/all-events @conn #inst"2021-06-01")


  (notify-user! @conn
    {::user/email "ebbinghaus@hhu.de" ::user/display-name "Björn"
     ::process/_participants [{:db/id 202}]}
    (collect/all-events @conn #inst"2021-06-01"))


  (let [db @conn]
    (spit "email.html" (get-in
                         (format/make-message
                           (build-mail db {::user/email "ebbinghaus@hhu.de" ::user/display-name "Björn"
                                           ::process/_participants [{:db/id 202}]}
                             (collect/all-events db #inst"2021-06-01")))
                         [:body 0 :content])))


  (sort-by :event/when (collect/get-new-argument-events @conn #inst"2021-06-01"))

  (d/pull-many @conn [{:argument/premise [:statement/content]}] [638 641 643 645 652])
  (d/pull @conn [::proposal/title] 207)

  :bla)

(defn send-email-notifications! [db events]
  (doseq [user (get-all-users-with-emails db)]
    (notify-user! db user events)))

(defstate notifier
  :start
  (chime/chime-at
    (chime/periodic-seq (Instant/now) (Duration/ofMinutes 60))
    (fn [time]
      (log/info "Notify!")
      (let [db (d/db conn)]
        (send-email-notifications! db
          (collect/all-events @conn (collect/minutes-ago 60))))))
  :stop
  (.close notifier))