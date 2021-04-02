(ns decide.features.notifications.notifier
  (:require
    [clojure.core.async :as async]
    [datahike.api :as d]
    [mount.core :refer [defstate]]
    [chime.core :as chime]
    [decide.features.notifications.collect :as collect]
    [decide.features.notifications.format-email :as format]
    [decide.models.argument :as argument]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]
    [decide.server-components.database :refer [conn]]
    [decide.server-components.email :refer [mailer-chan]]
    [taoensso.timbre :as log])
  (:import (java.time Instant Duration)))

(defn get-all-users-with-emails [db]
  (d/q '[:find [(pull ?user [::user/email ::user/display-name
                             {::process/_participants [::process/slug]}]) ...]
         :where
         [?user ::user/email]
         [?process ::process/participants ?user]]
    db))


(defn distinct-by-merge
  "Shallow-merges items from `coll` based upon the return value for `f`."
  [f coll]
  (reduce (fn [acc [_ g]] (conj acc (apply merge g))) [] (group-by f coll)))

(defn- assign-arguments-to-proposals [arguments proposals]
  (map second
    (let [indexed-proposals (into {} (map #(vector (::proposal/id %) %) proposals))]
      (reduce
        (fn [ps argument]
          (reduce
            (fn [ps id]
              (update-in ps [id ::proposal/arguments] conj argument))
            ps (map ::proposal/id (::proposal/_arguments argument))))
        indexed-proposals arguments))))


(defn- build-process-payload [db [slug events]]
  (let [{proposal-events :event.type/new-proposal
         argument-events :event.type/new-argument}
        (group-by :event/what events)

        arguments
        (->> argument-events
          (map :event/ref)
          (d/pull-many db [::argument/content
                           {::proposal/_arguments [::proposal/id ::proposal/title]}]))

        proposals
        (->> proposal-events
          (map :event/ref)
          (d/pull-many db [::proposal/id ::proposal/title])
          (map #(assoc % :new? true))

          ;; add proposals that aren't new, but that have new arguments
          (concat (mapcat ::proposal/_arguments arguments))
          (distinct-by-merge ::proposal/id)
          (assign-arguments-to-proposals arguments))]
    (merge
      {::process/proposals proposals}
      (d/pull db [::process/slug ::process/title] [::process/slug slug]))))

(defn- build-payload [db grouped-events user]
  {:user user
   :base-url "https://decide.ebbinghaus.me"                 ; FIXME Get this from the config
   :processes
   (map #(build-process-payload db %) grouped-events)})

(defn notify-user! [db user events]
  (let [process-slugs (map ::process/slug (get user ::process/_participants []))
        grouped-events (select-keys events process-slugs)]
    (when-not (empty? grouped-events)
      (log/debug "Notify " (::user/email user))
      (async/put! mailer-chan
        (format/make-message
          (build-payload db grouped-events user))))))

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
          (collect/events-by-slug db (collect/minutes-ago 60))))))
  :stop
  (.close notifier))