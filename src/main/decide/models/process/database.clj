(ns decide.models.process.database
  "A collection of all"
  (:require
   [clojure.set :as set]
   [clojure.spec.alpha :as s]
   [com.fulcrologic.guardrails.core :refer [=> >defn ?]]
   [datahike.api :as d]
   [datahike.core :as d.core]
   [decide.models.opinion :as opinion]
   [decide.models.process :as process]
   [decide.models.proposal :as-alias proposal]
   [decide.models.user :as user]
   [decide.server-components.database :as db]))

(def process-pattern
  [:db/id
   ::process/slug
   ::process/title
   ::process/description
   ::process/type
   ::process/proposals
   ::process/moderators
   :process/features
   ::process/participants])

(defn get-by-slug
  ([db slug]
   [d.core/db? ::process/slug => (? (s/keys))]
   (get-by-slug db slug process-pattern))
  ([db slug pattern]
   [d.core/db? ::process/slug vector? => (? (s/keys))]
   (d/q '[:find (pull ?e pattern) .
          :in $ pattern ?slug
          :where
          [?e ::process/slug ?slug]]
     db pattern slug)))

(>defn slug-in-use? [db slug]
  [d.core/db? ::process/slug => boolean?]
  (boolean (get-by-slug db slug [:db/id])))

(>defn ->add
  "Returns a transaction as data, ready to be transacted."
  [db {:keys [::process/slug ::process/title ::process/description
              ::process/type ::process/end-time ::process/moderators
              ::process/participants :process/features]
       :or {type ::process/type.public
            moderators []
            participants []
            features []}}]
  [d.core/db? (s/keys :req [::process/slug ::process/title ::process/description]
                :opt [::process/type ::process/end-time])
   => vector?]
  (if (slug-in-use? db slug)
    (throw (ex-info "Slug already in use" {:slug slug}))
    [(cond->
       {::process/slug slug
        ::process/title title
        ::process/description description
        ::process/type type
        ::process/proposals []                              ; Do not allow to set initial proposals as this may create conflicts with the nice id
        ::process/latest-id 0
        ::process/moderators (map #(find % ::user/id) moderators)
        :process/features (filter process/feature-set features)
        ::process/participants participants}
       end-time (assoc ::process/end-time end-time))]))

(>defn ->set-feature-set [process features]
  [(s/keys :req [:db/id :process/features]) (s/coll-of ::process/feature :kind set?) => vector?]
  (let [features (into #{} (filter process/feature-set) features)
        current-features (:process/features process)
        features-to-add (set/difference features current-features)
        features-to-remove (set/difference current-features features)]
    (into [] cat
      [[{:db/id (:db/id process) :process/features (vec features-to-add)}]
       (map #(vector :db/retract (:db/id process) :process/features %) features-to-remove)])))

(>defn ->update
  "Generates a transaction data to update an existing process.
  Any explicit nil key will be retracted."
  [existing-process new-process]
  [(s/keys :req [:db/id :process/features]) (s/keys) => vector?]
  (into [] cat
    [(mapv
       (fn [[k v]]
         (if (some? v)
           [:db/add (:db/id existing-process) k v]
           [:db/retract (:db/id existing-process) k]))
       (dissoc new-process ::process/slug :process/features))
     (when (contains? new-process :process/features)
       (->set-feature-set existing-process (:process/features new-process)))]))

(>defn ->upsert [db {::process/keys [slug] :as process}]
  [d.core/db? (s/keys :req [::process/slug])
   => vector?]
  (if-let [existing-process (get-by-slug db slug [:db/id :process/features])]
    (->update existing-process process)
    (->add db process)))

(defn ->add-moderator [process user]
  [[:db/add (:db/id process) ::process/moderators (:db/id user)]])

(defn ->remove-moderator [process user]
  [[:db/retract (:db/id process) ::process/moderators (:db/id user)]])

(defn ->enter [process user]
  [[:db/add (:db/id process) ::process/participants (:db/id user)]])

(def ->add-participant ->enter)

;; TODO What happens to proposals, votes, arguments...?
(defn ->remove-participant
  [process user]
  [[:db/retract (:db/id process) ::process/participants (:db/id user)]])

(defn get-public-processes [db]
  [d.core/db? => (s/coll-of (s/keys :req [::process/slug ::process/type]) :kind set?)]
  (set (d/q '[:find ?slug ?type
              :keys decide.models.process/slug decide.models.process/type
              :in $
              :where
              [?e ::process/slug ?slug]
              [(get-else $ ?e ::process/type ::process/type.public) ?type]
              [(= ?type ::process/type.public)]]
         db)))

(>defn get-private-processes [db user-lookup]
  [d.core/db? ::user/lookup => (s/coll-of (s/keys :req [::process/slug ::process/type]) :kind set?)]
  (set (d/q '[:find ?slug ?type
              :keys decide.models.process/slug decide.models.process/type
              :in $ ?user
              :where
              [(ground ::process/type.private) ?type]
              (or
                [?e ::process/participants ?user]
                [?e ::process/moderators ?user])
              [?e ::process/type ?type]
              [?e ::process/slug ?slug]]
         db user-lookup)))

(>defn get-all-processes
  ([db]
   [d.core/db? => (s/coll-of (s/keys :req [::process/slug ::process/type]) :kind set?)]
   (get-public-processes db))
  ([db user-lookup]
   [d.core/db? ::user/lookup => (s/coll-of (s/keys :req [::process/slug ::process/type]) :kind set?)]
   (set/union
     (get-public-processes db)
     (get-private-processes db user-lookup))))

(defn latest-nice-id [process]
  [d.core/db? ::process/slug => ::proposal/nice-id]
  (transduce (map ::proposal/nice-id) max 0 (::process/proposals process)))

(defn new-nice-id [process]
  (inc (latest-nice-id process)))

(>defn get-number-of-participants [db slug]
  [d.core/db? ::process/slug => nat-int?]
  (or (d/q '[:find (count ?e) .
             :in $ ?process
             :where
             [?process ::process/participants ?e]]
        db [::process/slug slug])
    0))

(>defn get-winner [db process]
  [d.core/db? (s/keys :req [::process/slug]) => (? ::proposal/proposal)]
  (let [proposals-with-votes
        (->> (find process ::process/slug)
          (d/pull db [{::process/proposals
                       [::proposal/id
                        ::proposal/created
                        {::proposal/opinions [::opinion/value]}
                        {::proposal/parents [::proposal/id]}]}])
          ::process/proposals
          (map opinion/votes))]
    (process/winner proposals-with-votes)))

(defn has-access? [process user]
  (and process
    (or
      (not= ::process/type.private (::process/type process))
      (contains? (::process/participants process) user)
      (contains? (::process/moderators process) user))))

(defn get-entity-by-slug [db slug]
  (when-let [process-eid (d/q '[:find ?e . :in $ ?slug :where [?e ::process/slug ?slug]] db slug)]
    (d/entity db process-eid)))

(defn total-votes
  "Get the total number of votes for a process."
  [process]
  (or
    (d/q
      '[:find (count ?opinion) .
        :in $ ?process
        :where
        [?process ::process/proposals ?proposal]
        [?proposal ::proposal/opinions ?opinion]
        [?opinion :decide.models.opinion/value]]
      (d/entity-db process) (:db/id process))
    0))

(defn add-moderator! [conn process-lookup moderator-id new-moderator-lookup]
  (db/transact-as conn [::user/id moderator-id]
    {:tx-data [[:db/add process-lookup ::process/moderators new-moderator-lookup]]}))
