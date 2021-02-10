(ns decide.models.process
  (:require
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [com.fulcrologic.guardrails.core :refer [>defn => | <-]]
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [datahike.api :as d]
    [datahike.core :as d.core]
    [decide.models.authorization :as auth]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]))

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

(s/def ::ident (s/tuple #{::slug} ::slug))
(s/def ::lookup (s/or :ident ::ident :db/id pos-int?))

(>defn get-all-slugs [db]
  [d.core/db? => (s/coll-of ::slug :distinct true)]
  (d/q '[:find [?slug ...]
         :where
         [_ ::slug ?slug]]
    db))

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

(>defn tx-map [{::keys [title slug description moderator-lookups]}]
  [(s/keys :req [::title ::slug ::description]) => (s/keys :req [::title ::slug ::description ::latest-id])]
  {::slug slug
   ::title title
   ::description description
   ::moderators moderator-lookups
   ::proposals []
   ::latest-id 0})

;; region API

(>defn add-process! [conn user-id {::keys [slug title description] :as process}]
  [d.core/conn? ::user/id (s/keys :req [::slug ::title ::description]) => map?]
  (let [db (d/db conn)
        moderator-id user-id]
    (cond
      (not (s/valid? ::slug slug))
      (throw (ex-info "Slug not valid" {:explain (s/explain-data ::slug slug)}))

      (not (s/valid? ::title title))
      (throw (ex-info "Title not valid" {:explain (s/explain-data ::title title)}))

      (not (s/valid? ::description description))
      (throw (ex-info "Description not valid" {:explain (s/explain-data ::description description)}))

      (slug-in-use? db slug) (throw (ex-info "Slug already in use" {:slug slug}))

      :else
      (d/transact conn
        [(merge
           (tx-map
             {::slug slug
              ::title title
              ::description description
              ::moderator-lookups [[::user/id moderator-id]]})
           process)
         [:db/add "datomic.tx" :db/txUser [::user/id user-id]]]))))



(>defn is-moderator? [db process-lookup user-id]
  [d.core/db? ::lookup ::user/id => boolean?]
  (contains?
    (into #{} (map ::user/id)
      (::moderators (d/pull db [{::moderators [::user/id]}] process-lookup)))
    user-id))

(>defn update-process! [conn user-id {::keys [slug] :as process}]
  [d.core/conn? ::user/id (s/keys :req [::slug] :opt [::title ::description ::end-time]) => map?]
  (let [db (d/db conn)]
    (cond
      (not user-id)
      (throw (ex-info "User not logged in!" {}))

      (not (s/valid? (s/keys :req [::slug] :opt [::title ::description ::end-time]) process))
      (throw (ex-info "Parameter not valid" {:explain (s/explain-data (s/keys :req [::slug] :opt [::title ::description ::end-time]) process)}))

      (not (is-moderator? db [::slug slug] user-id))
      (throw (ex-info "User is not moderator of this process" {::user/id user-id ::slug slug}))

      :else
      (let [facts (for [[k v] (dissoc process ::slug)]
                    (if v
                      [:db/add [::slug slug] k v]
                      [:db/retract [::slug slug] k]))
            {:keys [db-after]}
            (d/transact conn
              (conj facts [:db/add "datomic.tx" :db/txUser [::user/id user-id]]))]
        db-after))))

(>defn get-number-of-participants [db slug]
  [d.core/db? ::slug => nat-int?]
  (-> db
    (d/pull [::participants] [::slug slug])
    ::participants
    count))

(>defn enter! [conn process-lookup user-lookup]
  [d.core/conn? ::lookup ::user/lookup => map?]
  (d/transact conn [[:db/add process-lookup ::participants user-lookup]]))

;; region API
(defn check-slug-exists [{::pc/keys [mutate] :as mutation}]
  (assoc mutation
    ::pc/mutate
    (fn [{:keys [db] :as env} {::keys [slug] :as params}]
      (if (slug-in-use? db slug)
        (mutate env params)
        (throw (ex-info "Slug is not in use!" {:slug slug}))))))

(defmutation add-process [{:keys [conn AUTH/user-id] :as env} {::keys [slug] :as process}]
  {::pc/params [::title ::slug ::description]
   ::pc/output [::slug]
   ::pc/transform auth/check-logged-in}
  (let [{:keys [db-after]} (add-process! conn user-id process)]
    {::slug slug
     ::p/env (assoc env :db db-after)}))

(defmutation update-process [{:keys [conn AUTH/user-id] :as env} {::keys [slug] :as process}]
  {::pc/params [::title ::slug ::description]
   ::pc/output [::slug]
   ::s/params (s/keys :req [::slug])
   ::pc/transform (comp auth/check-logged-in check-slug-exists)}
  (let [db-after (update-process! conn user-id process)]
    {::slug slug
     ::p/env (assoc env :db db-after)}))

(defmutation add-moderator [{:keys [conn db AUTH/user-id] :as env} {::keys [slug] email :email}]
  {::pc/output [::user/id]
   ::pc/transform (comp auth/check-logged-in check-slug-exists)}
  (if (slug-in-use? db slug)
    (if-let [{::user/keys [id]} (and (user/email-in-db? db email) (user/get-by-email db email))]
      (let [{:keys [db-after]} (d/transact conn [[:db/add [::slug slug] ::moderators [::user/id id]]
                                                 [:db/add "datomic.tx" :db/txUser [::user/id user-id]]])]
        {::user/id id
         ::p/env (assoc env :db db-after)})
      (throw (ex-info "User with this email doesn't exist!" {:email email})))
    (throw (ex-info "Slug is not in use!" {:slug slug}))))

(defmutation enter [{:keys [conn AUTH/user-id]} {user :user-id
                                                 slug ::slug}]
  {::pc/params [::slug :user-id]
   ::pc/transform (comp auth/check-logged-in check-slug-exists)}
  (when (= user-id user)
    (enter! conn [::slug slug] [::user/id user])
    nil))

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
                    [(assoc new-proposal :db/id "new-proposal")
                     [:db/add [::slug slug] ::proposals "new-proposal"]
                     [:db/add "datomic.tx" :db/txUser [::user/id user-id]]])]
    {::proposal/id real-id
     :tempids {id real-id}
     ::p/env (assoc env :db (:db-after tx-report))}))


(def resolvers
  [add-process
   update-process

   add-moderator

   enter

   add-proposal])

;; endregion