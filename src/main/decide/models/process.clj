(ns decide.models.process
  (:require
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [com.fulcrologic.guardrails.core :refer [>defn => | <- ?]]
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [datahike.api :as d]
    [datahike.core :as d.core]
    [decide.models.authorization :as auth]
    [decide.models.opinion :as opinion]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user])
  (:import (java.util Date)))

(def schema
  [{:db/ident ::slug
    :db/valueType :db.type/string
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one}

   {:db/ident ::title
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}

   {:db/ident ::description
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}

   {:db/ident ::proposals
    :db/cardinality :db.cardinality/many
    :db/valueType :db.type/ref
    :db/isComponent true}

   {:db/ident ::type
    :db/cardinality :db.cardinality/one
    :db/valueType :db.type/keyword}

   {:db/ident ::participants
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many}

   {:db/ident ::moderators
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many}

   {:db/ident ::latest-id
    :db/cardinality :db.cardinality/one
    :db/valueType :db.type/long}

   {:db/ident ::end-time
    :db/cardinality :db.cardinality/one
    :db/valueType :db.type/instant}])

(def slug-pattern #"^[a-z0-9]+(?:-[a-z0-9]+)*$")
(s/def ::slug (s/and string? (partial re-matches slug-pattern)))
(s/def ::title (s/and string? (complement str/blank?)))
(s/def ::description string?)
(s/def ::latest-id (s/and int? #(<= 0 %)))
(s/def ::end-time (s/nilable inst?))
(s/def ::type #{::type.public ::type.private})

(s/def ::ident (s/tuple #{::slug} ::slug))
(s/def ::lookup (s/or :ident ::ident :db/id pos-int?))

(defn get-public-processes [db]
  [d.core/db? => (s/coll-of (s/keys :req [::slug ::type]) :kind set?)]
  (set (d/q '[:find ?slug ?type
              :keys decide.models.process/slug decide.models.process/type
              :in $
              :where
              [?e ::slug ?slug]
              [(get-else $ ?e ::type ::type.public) ?type]
              [(= ?type ::type.public)]]
         db)))

(>defn get-private-processes [db user-lookup]
  [d.core/db? ::user/lookup => (s/coll-of (s/keys :req [::slug ::type]) :kind set?)]
  (set (d/q '[:find ?slug ?type
              :keys decide.models.process/slug decide.models.process/type
              :in $ ?user
              :where
              [(ground ::type.private) ?type]
              (or
                [?e ::participants ?user]
                [?e ::moderators ?user])
              [?e ::type ?type]
              [?e ::slug ?slug]]
         db user-lookup)))

(>defn get-all-processes
  ([db]
   [d.core/db? => (s/coll-of (s/keys :req [::slug ::type]) :kind set?)]
   (get-public-processes db))
  ([db user-lookup]
   [d.core/db? ::user/lookup => (s/coll-of (s/keys :req [::slug ::type]) :kind set?)]
   (set/union
     (get-public-processes db)
     (get-private-processes db user-lookup))))

(>defn new-nice-id! [conn slug]
  [d.core/conn? ::slug => ::proposal/nice-id]
  (let [{::keys [latest-id]
         :keys [db/id]}
        (d/pull (d/db conn)
          [:db/id
           [::latest-id :default 0]]
          [::slug slug])]
    (d/transact conn [[:db/add id ::latest-id (inc latest-id)]])
    (inc latest-id)))

(>defn slug-in-use? [db slug]
  [d.core/db? ::slug => boolean?]
  (boolean (d/q '[:find ?e .
                  :in $ ?slug
                  :where
                  [?e :decide.models.process/slug ?slug]]
             db slug)))

(>defn get-no-of-contributors [db slug]
  [d.core/db? ::slug => (s/spec #(<= 0 %))]
  (let [{::keys [proposals]} (d/pull db [{::proposals [:db/id]}] [::slug slug])
        proposal-db-ids (map :db/id proposals)
        commenter (apply set/union (map (partial proposal/get-users-who-made-an-argument db) proposal-db-ids))
        authors (into #{}
                  (map (comp :db/id ::proposal/original-author))
                  (d/pull-many db [{::proposal/original-author [:db/id]}] proposal-db-ids))
        voters (apply set/union (map (partial proposal/get-voters db) proposal-db-ids))]
    (count (set/union commenter authors voters))))

(>defn in-past? [date]
  [any? => boolean?]
  (neg? (compare date (Date.))))

(defn is-over?
  "Checks if a processes is over based on its end-time and the current datetime.
   Returns false if process has no end-time"
  [db process-lookup]
  [d.core/db? ::lookup]
  (if-let [end-time (::end-time (d/pull db [::end-time] process-lookup))]
    (in-past? end-time)
    false))

(comment
  ;; TODO implement
  (defn has-access-to-process? [db process-lookup user-lookup]
    (d/q '[:find ?e
           :in $ ?process ?user
           :where
           (or
             [?process ::participants ?user]
             [?process ::moderators ?user])])))

(>defn ->add
  "Returns a transaction as data, ready to be transacted."
  [db {::keys [slug title description
               type end-time moderators
               participants]
       :or {type ::type.public
            moderators []
            participants []}}]
  [d.core/db? (s/keys :req [::slug ::title ::description] :opt [::type ::end-time]) => vector?]
  (if (slug-in-use? db slug)
    (throw (ex-info "Slug already in use" {:slug slug}))
    [(cond->
       {::slug slug
        ::title title
        ::description description
        ::type type
        ::proposals []                                      ; Do not allow to set initial proposals as this may create conflicts with the nice id
        ::latest-id 0
        ::moderators moderators
        ::participants participants}
       end-time (assoc ::end-time end-time))]))


(>defn is-moderator? [db process-lookup user-id]
  [d.core/db? ::lookup ::user/id => boolean?]
  (contains?
    (into #{} (map ::user/id)
      (::moderators (d/pull db [{::moderators [::user/id]}] process-lookup)))
    user-id))

(>defn ->update
  "Generates a transaction data to update an existing process.
  Any falsy key will be retracted."
  [{::keys [slug] :as process}]
  [(s/keys :req [::slug] :opt [::title ::description ::end-time]) => vector?]
  (let [process-ident [::slug slug]]
    (mapv
      (fn [[k v]]
        (if v
          [:db/add process-ident k v]
          [:db/retract process-ident k]))
      (dissoc process ::slug))))

(>defn ->upsert [db {::keys [slug] :as process}]
  [d.core/db? (s/keys :req [::slug] :opt [::title ::description ::type ::end-time])
   => vector?]
  (if (slug-in-use? db slug)
    (->update process)
    (->add db process)))

(>defn get-number-of-participants [db slug]
  [d.core/db? ::slug => nat-int?]
  (-> db
    (d/pull [::participants] [::slug slug])
    ::participants
    count))

(defn ->enter [process-lookup user-lookups]
  [[:db/add process-lookup ::participants user-lookups]])

(>defn get-most-approved-proposals
  "From a collection of `proposals`, return a subset of `proposals` that have the most approval"
  [proposals]
  [(s/coll-of (s/keys :req [::proposal/pro-votes]))
   => (s/coll-of ::proposal/proposal) | #(set/subset? (set %) (set proposals))]
  (let [voting-groups (group-by ::proposal/pro-votes proposals)]
    (get voting-groups (apply max (keys voting-groups)) [])))

(>defn remove-parents
  "From a list of `proposals`, remove all parents that have children in the collection."
  [proposals]
  [(s/coll-of ::proposal/proposal)
   => (s/coll-of ::proposal/proposal) | #(set/subset? (set %) (set proposals))]
  (let [parent-ids (set (map ::proposal/id (mapcat ::proposal/parents proposals)))]
    (remove (comp parent-ids ::proposal/id) proposals)))

(>defn solve-draw
  "Takes a collection of `proposals` and returns a single winner."
  [proposals]
  [(s/coll-of ::proposal/proposal)
   => ::proposal/proposal | #(contains? (set proposals) %)]
  (->> proposals
    (sort-by ::proposal/created)
    reverse
    first))

(s/keys)

(>defn get-winner [db process]
  [d.core/db? (s/keys :req [::slug]) => (? ::proposal/proposal)]
  (let [most-approved-proposals
        (->> (find process ::slug)
          (d/pull db [{::proposals
                       [::proposal/id
                        ::proposal/created
                        {::proposal/opinions [::opinion/value]}
                        {::proposal/parents [::proposal/id]}]}])
          ::proposals
          (map opinion/votes)
          get-most-approved-proposals)]

    (if (< 2 (count most-approved-proposals))
      (first most-approved-proposals)
      (solve-draw most-approved-proposals))))

;; region API
(defn check-slug-exists [{::pc/keys [mutate] :as mutation}]
  (assoc mutation
    ::pc/mutate
    (fn [{:keys [db] :as env} {::keys [slug] :as params}]
      (if (slug-in-use? db slug)
        (mutate env params)
        (throw (ex-info "Slug is not in use!" {:slug slug}))))))

(defn needs-moderator [{::pc/keys [mutate] :as mutation}]
  (assoc mutation
    ::pc/mutate
    (fn [{:keys [db AUTH/user-id] :as env} {::keys [slug] :as params}]
      (if (is-moderator? db [::slug slug] user-id)
        (mutate env params)
        (throw (ex-info "Need moderation role for this operation" {::slug slug
                                                                   ::user/id user-id}))))))

(defn process-still-going [{::pc/keys [mutate] :as mutation}]
  (assoc mutation
    ::pc/mutate
    (fn [{:keys [db] :as env} {::keys [slug] :as params}]
      (if-not (is-over? db [::slug slug])
        (mutate env params)
        (throw (ex-info "The process is already over" {::slug slug}))))))


(defmutation add-process [{:keys [conn db AUTH/user-id] :as env}
                          {::keys [slug] :keys [participant-emails] :as process}]
  {::pc/params [::title ::slug ::description ::end-time ::type
                :participant-emails]
   ::pc/output [::slug]
   ::s/params (s/keys
                :req [::slug ::title ::description]
                :opt [::end-time ::type])
   ::pc/transform auth/check-logged-in}
  (let [user-ident [::user/id user-id]
        existing-emails (filter #(user/email-in-db? db %) participant-emails)
        {:keys [db-after]}
        (d/transact conn
          {:tx-data
           (conj (->add db
                   (-> process
                     (update ::moderators conj user-ident)  ; remove that here? let the user come through parameters?
                     (update ::participants concat (map #(vector ::user/email %) existing-emails))))
             [:db/add "datomic.tx" :db/txUser user-ident])})]
    {::slug slug
     ::p/env (assoc env :db db-after)}))

(defmutation update-process [{:keys [conn AUTH/user-id] :as env} {::keys [slug] :as process}]
  {::pc/params [::slug ::title ::description ::end-time ::type]
   ::pc/output [::slug]
   ::s/params (s/keys :req [::slug] :opt [::title ::description ::end-time ::type])
   ::pc/transform (comp auth/check-logged-in check-slug-exists needs-moderator)}
  (let [{:keys [db-after]}
        (d/transact conn
          {:tx-data
           (-> process ->update (conj [:db/add "datomic.tx" :db/txUser [::user/id user-id]]))})]
    {::slug slug
     ::p/env (assoc env :db db-after)}))

(defn add-moderator! [conn process-lookup moderator-id new-moderator-lookup]
  (d/transact conn
    [[:db/add process-lookup ::moderators new-moderator-lookup]
     [:db/add "datomic.tx" :db/txUser [::user/id moderator-id]]]))

(defmutation add-moderator [{:keys [conn db AUTH/user-id] :as env} {::keys [slug] email ::user/email}]
  {::pc/output [::user/id]
   ::s/params (s/keys :req [::slug ::user/email])
   ::pc/transform (comp auth/check-logged-in check-slug-exists needs-moderator)}
  (if-let [{::user/keys [id]} (and (user/email-in-db? db email) (user/get-by-email db email))]
    (let [{:keys [db-after]} (add-moderator! conn [::slug slug] user-id [::user/id id])]
      {::user/id id
       ::p/env (assoc env :db db-after)})
    (throw (ex-info "User with this email doesn't exist!" {:email email}))))



(defmutation add-participant [{:keys [conn db AUTH/user-id] :as env}
                              {user-emails :user-emails slug ::slug}]
  {::pc/params [::slug ::user/email]
   ::pc/output [::slug]
   ::s/params (s/keys :req [::slug])
   ::pc/transform (comp auth/check-logged-in check-slug-exists needs-moderator)}
  (let [user-lookups
        (for [user-email user-emails
              :when (user/email-in-db? db user-email)]
          [::user/email user-email])

        {:keys [db-after]}
        (d/transact conn
          (conj
            (->enter [::slug slug] user-lookups)
            [:db/add "datomic.tx" :db/txUser [::user/id user-id]]))]
    {::slug slug
     ::p/env (assoc env :db db-after)}))

(defmutation add-proposal [{:keys [conn AUTH/user-id] :as env}
                           {::proposal/keys [id title body parents arguments]
                            ::keys [slug]
                            :or {parents [] arguments []}}]
  {::pc/params [::slug
                ::proposal/id ::proposal/title ::proposal/body ::proposal/parents ::proposal/arguments]
   ::pc/output [::proposal/id]
   ::pc/transform (comp auth/check-logged-in check-slug-exists)}
  (let [{real-id ::proposal/id :as new-proposal}
        (proposal/tx-map #::proposal{:nice-id (new-nice-id! conn slug)
                                     :title title
                                     :body body
                                     :parents (map #(find % ::proposal/id) parents)
                                     :argument-idents arguments
                                     :original-author [::user/id user-id]})
        tx-report (d/transact conn
                    (concat
                      (->enter [::slug slug] [::user/id user-id])
                      [(assoc new-proposal :db/id "new-proposal")
                       [:db/add [::slug slug] ::proposals "new-proposal"]
                       [:db/add "datomic.tx" :db/txUser [::user/id user-id]]]))]
    {::proposal/id real-id
     :tempids {id real-id}
     ::p/env (assoc env :db (:db-after tx-report))}))


(def resolvers
  [add-process
   update-process

   add-moderator

   add-participant

   add-proposal])

;; endregion