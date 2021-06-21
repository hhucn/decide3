(ns decide.models.process.database
  "A collection of all"
  (:require
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [com.fulcrologic.guardrails.core :refer [>defn => | <- ?]]
    [datahike.api :as d]
    [datahike.core :as d.core]
    [decide.models.opinion :as opinion]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.models.proposal.database :as proposal.db]
    [decide.models.user :as user]))

(>defn slug-in-use? [db slug]
  [d.core/db? ::process/slug => boolean?]
  (boolean (d/q '[:find ?e .
                  :in $ ?slug
                  :where
                  [?e ::process/slug ?slug]]
             db slug)))

(>defn ->add
  "Returns a transaction as data, ready to be transacted."
  [db {::process/keys [slug title description
                       type end-time moderators
                       participants features]
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
        ::process/features (filter process/feature-set features)
        ::process/participants participants}
       end-time (assoc ::process/end-time end-time))]))

(>defn ->update
  "Generates a transaction data to update an existing process.
  Any falsy key will be retracted."
  [{::process/keys [slug] :as process}]
  [(s/keys :req [::process/slug]) => vector?]
  (let [process-ident [::process/slug slug]]
    (mapv
      (fn [[k v]]
        (if v
          [:db/add process-ident k v]
          [:db/retract process-ident k]))
      (dissoc process ::process/slug))))

(>defn ->upsert [db {::process/keys [slug] :as process}]
  [d.core/db? (s/keys :req [::process/slug])
   => vector?]
  (if (slug-in-use? db slug)
    (->update process)
    (->add db process)))

(defn ->enter [process-lookup user-lookups]
  [[:db/add process-lookup ::process/participants user-lookups]])

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

(>defn new-nice-id! [conn slug]
  [d.core/conn? ::process/slug => ::proposal/nice-id]
  (let [{::process/keys [latest-id]
         :keys [db/id]}
        (d/pull (d/db conn)
          [:db/id
           [::process/latest-id :default 0]]
          [::process/slug slug])]
    (d/transact conn [[:db/add id ::process/latest-id (inc latest-id)]])
    (inc latest-id)))


(>defn ^:deprecated get-no-of-contributors
  "DEPRECATED - This is way too slow. Use `get-number-of-participants` instead."
  [db slug]
  [d.core/db? ::process/slug => (s/spec #(<= 0 %))]
  (let [{::process/keys [proposals]} (d/pull db [{::process/proposals [:db/id]}] [::process/slug slug])
        proposal-db-ids (map :db/id proposals)
        commenter (apply set/union (map (partial proposal.db/get-users-who-made-an-argument db) proposal-db-ids))
        authors (into #{}
                  (map (comp :db/id ::proposal/original-author))
                  (d/pull-many db [{::proposal/original-author [:db/id]}] proposal-db-ids))
        voters (apply set/union (map (partial proposal.db/get-voters db) proposal-db-ids))]
    (count (set/union commenter authors voters))))

(>defn get-number-of-participants [db slug]
  [d.core/db? ::process/slug => nat-int?]
  (-> db
    (d/pull [::process/participants] [::process/slug slug])
    ::process/participants
    count))

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