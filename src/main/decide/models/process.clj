(ns decide.models.process
  (:require
    [clojure.spec.alpha :as s]
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [datahike.api :as d]
    [datahike.core :as d.core]
    [decide.models.authorization :as auth]
    [decide.models.proposal :as proposal]
    [com.fulcrologic.guardrails.core :refer [>defn => | <-]]
    [taoensso.timbre :as log]))

(def schema
  [{:db/ident ::slug
    :db/valueType :db.type/string
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one}

   {:db/ident ::proposals
    :db/cardinality :db.cardinality/many
    :db/valueType :db.type/ref
    :db/isComponent true}

   {:db/ident ::latest-id
    :db/cardinality :db.cardinality/one
    :db/valueType :db.type/long}])

(s/def ::slug string?)
(s/def ::latest-id pos-int?)

(s/def ::ident (s/tuple #{::slug} ::slug))

(>defn get-all-process-slugs [db]
  [d.core/db? => (s/coll-of ::slug :distinct true)]
  (d/q '[:find [?slug ...]
         :where
         [_ ::slug ?slug]]
    db))

(defn new-nice-id! [conn slug]
  [d.core/conn? ::slug => ::proposal/nice-id]
  (let [{::keys [latest-id]
         :keys [db/id]}
        (d/pull (d/db conn)
          [:db/id
           [::latest-id :default 0]]
          [::slug slug])]
    (d/transact conn [[:db/add id ::latest-id (inc latest-id)]])
    (inc latest-id)))

(defresolver resolve-all-processes [{:keys [db]} _]
  {::pc/input #{}
   ::pc/output [{:all-processes [::slug]}]}
  {:all-processes
   (map #(hash-map ::slug %) (get-all-process-slugs db))})

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
                            :or    {parents [] arguments []}}]
  {::pc/params    [::slug
                   ::proposal/id ::proposal/title ::proposal/body ::proposal/parents ::proposal/arguments]
   ::pc/output    [::proposal/id]
   ::pc/transform auth/check-logged-in}
  (let [real-id (d.core/squuid)
        tx-report (d/transact conn
                    [(proposal/tx-data-add #::proposal{:id real-id
                                                       :nice-id (new-nice-id! conn slug)
                                                       :title title
                                                       :body body
                                                       :parents parents
                                                       :argument-idents arguments
                                                       :user-ident [:user/id user-id]})
                     [:db/add [::slug slug] ::proposals (str real-id)]])]
    {:tempids {id real-id}
     ::p/env (assoc env :db (:db-after tx-report))
     ::proposal/id real-id}))

(def resolvers
  [resolve-all-processes
   resolve-proposals
   resolve-proposal-process
   resolve-nice-proposal
   add-proposal])