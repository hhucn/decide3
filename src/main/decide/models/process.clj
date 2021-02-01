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
    :db/valueType :db.type/long}])

(def slug-pattern #"^[a-z0-9]+(?:-[a-z0-9]+)*$")
(s/def ::slug (s/and string? (partial re-matches slug-pattern)))
(s/def ::title (s/and string? (complement str/blank?)))
(s/def ::description string?)
(s/def ::latest-id (s/and int? #(<= 0 %)))

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

(defresolver resolve-all-processes [{:keys [db]} _]
  {::pc/input #{}
   ::pc/output [{:all-processes [::slug]}]}
  {:all-processes
   (map #(hash-map ::slug %) (get-all-slugs db))})

(defresolver resolve-process [{:keys [db]} {::keys [slug]}]
  {::pc/input #{::slug}
   ::pc/output [::title ::description]}
  (d/pull db [::title ::description] [::slug slug]))

(defresolver resolve-process-moderators [{:keys [db]} {::keys [slug]}]
  {::pc/input #{::slug}
   ::pc/output [{::moderators [::user/id]}]}
  (d/pull db [{::moderators [::user/id]}] [::slug slug]))

(defresolver resolve-user-moderated-processes [{:keys [db]} {::user/keys [id]}]
  {::pc/output [{::user/moderated-processes [::slug]}]}
  (if (user/exists? db id)
    (d/pull db [{::_moderators :as ::user/moderated-processes [::slug]}] [::user/id id])
    (throw (ex-info "User does not exist" {::user/id id}))))

(defresolver resolve-authors [{:keys [db]} {::keys [proposals]}]
  {::pc/input #{::proposals}
   ::pc/output [{::authors [::user/id]}
                ::no-of-authors]}
  (let [authors (vec
                  (set
                    (for [{::proposal/keys [id original-author]} proposals]
                      (if-let [user-id (::user/id original-author)]
                        {::user/id user-id}
                        (-> db
                          (d/pull [{::proposal/original-author [::user/id]}] [::proposal/id id])
                          ::proposal/original-author)))))]
    {::authors authors
     ::no-of-authors (count authors)}))

(defresolver resolve-no-of-contributors [{:keys [db]} {::keys [slug]}]
  {::no-of-contributors (get-no-of-contributors db slug)})

(defmutation add-process [{:keys [conn db AUTH/user-id] :as env} {::keys [title slug description]}]
  {::pc/params [::title ::slug ::description]
   ::pc/output [::slug]}
  (let [moderator-id user-id]
    (cond
      (not user-id)
      (throw (ex-info "User not logged in!" {}))

      (not (s/valid? ::slug slug))
      (throw (ex-info "Slug not valid" {:explain (s/explain-data ::slug slug)}))

      (not (s/valid? ::title title))
      (throw (ex-info "Title not valid" {:explain (s/explain-data ::title title)}))

      (not (s/valid? ::description description))
      (throw (ex-info "Description not valid" {:explain (s/explain-data ::description description)}))

      (slug-in-use? db slug) (throw (ex-info "Slug already in use" {:slug slug}))

      :else
      (let [{:keys [db-after]}
            (d/transact conn
              [(tx-map
                 {::slug slug
                  ::title title
                  ::description description
                  ::moderator-lookups [[::user/id moderator-id]]})
               [:db/add "datomic.tx" :db/txUser [::user/id user-id]]])]
        {::slug slug
         ::p/env (assoc env :db db-after)}))))

(>defn enter! [conn process-lookup user-lookup]
  [d.core/conn? ::lookup ::user/lookup => map?]
  (d/transact conn [[:db/add process-lookup ::participants user-lookup]]))

(defmutation enter [{:keys [conn AUTH/user-id]} {user :user-id
                                                 slug ::slug}]
  {::pc/params [::slug :user-id]
   ::pc/transform auth/check-logged-in}
  (when (= user-id user)
    (enter! conn [::slug slug] [::user/id user])
    nil))

(>defn get-number-of-participants [db slug]
  [d.core/db? ::slug => nat-int?]
  (-> db
    (d/pull [::participants] [::slug slug])
    ::participants
    count))

(defresolver resolve-no-of-participants [{:keys [db]} {::keys [slug]}]
  {::pc/input #{::slug}
   ::pc/output [::no-of-participants]}
  {::no-of-participants (get-number-of-participants db slug)})

(defresolver resolve-proposals [{:keys [db]} {::keys [slug]}]
  {::pc/input #{::slug}
   ::pc/output [{::proposals [::proposal/id]}]}
  (or
    (d/pull db [{::proposals [::proposal/id]}] [::slug slug])
    {::proposals []}))

(defresolver resolve-no-of-proposals [_ {::keys [proposals]}]
  {::no-of-proposals (count proposals)})

(defresolver resolve-nice-proposal [{:keys [db]} {::proposal/keys [nice-ident]}]
  {::pc/input #{::proposal/nice-ident}
   ::pc/output [::proposal/id]}
  (let [[slug nice-id] nice-ident]
    {::proposal/id
     (d/q '[:find ?id .
            :in $ ?process ?nice-id
            :where
            [?process ::proposals ?proposal]
            [?proposal ::proposal/nice-id ?nice-id]
            [?proposal ::proposal/id ?id]]
       db [::slug slug] nice-id)}))


(defresolver resolve-proposal-process [{:keys [db]} {::proposal/keys [id]}]
  {::pc/input #{::proposal/id}
   ::pc/output [::slug]}
  (::_proposals (d/pull db [{::_proposals [::slug]}] [::proposal/id id])))

(defmutation add-proposal [{:keys [conn AUTH/user-id] :as env}
                           {::proposal/keys [id title body parents arguments]
                            ::keys [slug]
                            :or {parents [] arguments []}}]
  {::pc/params [::slug
                ::proposal/id ::proposal/title ::proposal/body ::proposal/parents ::proposal/arguments]
   ::pc/output [::proposal/id]
   ::pc/transform auth/check-logged-in}
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
  [resolve-all-processes
   resolve-process
   resolve-no-of-contributors
   add-process
   enter
   resolve-no-of-participants
   resolve-no-of-proposals
   resolve-authors

   resolve-user-moderated-processes
   resolve-process-moderators

   resolve-proposals
   resolve-proposal-process
   resolve-nice-proposal
   add-proposal])

;; endregion