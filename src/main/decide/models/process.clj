(ns decide.models.process
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [com.fulcrologic.guardrails.core :refer [>defn => | <-]]
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [datahike.api :as d]
    [datahike.core :as d.core]
    [decide.models.authorization :as auth]
    [decide.models.proposal :as proposal]
    [taoensso.timbre :as log]))

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

   {:db/ident ::latest-id
    :db/cardinality :db.cardinality/one
    :db/valueType :db.type/long}])

(def slug-pattern #"^[a-z0-9]+(?:-[a-z0-9]+)*$")
(s/def ::slug (s/and string? (partial re-matches slug-pattern)))
(s/def ::title (s/and string? (complement str/blank?)))
(s/def ::description string?)
(s/def ::latest-id (s/and int? #(<= 0 %)))

(s/def ::ident (s/tuple #{::slug} ::slug))

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

(>defn tx-map [{::keys [title slug description]}]
  [(s/keys :req [::title ::slug ::description]) => (s/keys :req [::title ::slug ::description ::latest-id])]
  {::slug slug
   ::title title
   ::description description
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


(defmutation add-process [{:keys [conn db AUTH/user-id] :as env} {::keys [title slug description]}]
  {::pc/params [::title ::slug ::description]
   ::pc/output [::slug]}
  (cond
    user-id nil
    (s/valid? ::slug title) nil
    (s/valid? ::title title) nil
    (s/valid? ::description description) nil
    (slug-in-use? db slug) nil
    :else
    (let [tx-report (d/transact! conn [(tx-map {::slug slug ::title title ::description description})])]
      {::slug slug
       ::p/env (assoc env :db (:db-after tx-report))})))

(defresolver resolve-proposals [{:keys [db]} {::keys [slug]}]
  {::pc/input #{::slug}
   ::pc/output [{::proposals [::proposal/id]}]}
  (or
    (d/pull db [{::proposals [::proposal/id]}] [::slug slug])
    {::proposals []}))

(defresolver resolve-nice-proposal [{:keys [db]} {::proposal/keys [nice-ident]}]
  {::pc/input #{::proposal/nice-ident}
   ::pc/output [::proposal/id]}
  (let [[slug nice-id] nice-ident]
    {::proposal/id
     (log/spy :debug
       (d/q '[:find ?id .
              :in $ ?process ?nice-id
              :where
              [?process ::proposals ?proposal]
              [?proposal ::proposal/nice-id ?nice-id]
              [?proposal ::proposal/id ?id]]
         db [::slug slug] nice-id))}))


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
                                     :original-author user-id})
        tx-report (d/transact conn
                    [(assoc new-proposal :db/id "new-proposal")
                     [:db/add [::slug slug] ::proposals "new-proposal"]])]
    {::proposal/id real-id
     :tempids {id real-id}
     ::p/env (assoc env :db (:db-after tx-report))}))


(def resolvers
  [resolve-all-processes
   resolve-process
   add-process

   resolve-proposals
   resolve-proposal-process
   resolve-nice-proposal
   add-proposal])

;; endregion