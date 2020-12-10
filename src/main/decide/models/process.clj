(ns decide.models.process
  (:require
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [datahike.api :as d]
    [decide.models.authorization :as auth]
    [decide.models.proposal :as proposal]
    [taoensso.timbre :as log]))

(def schema
  [{:db/ident       ::slug
    :db/valueType   :db.type/string
    :db/unique      :db.unique/identity
    :db/cardinality :db.cardinality/one}

   {:db/ident       ::proposals
    :db/cardinality :db.cardinality/many
    :db/valueType   :db.type/ref
    :db/isComponent true}])

(defn get-all-process-slugs [db]
  (d/q '[:find [?slug ...]
         :where
         [_ ::slug ?slug]]
    db))

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

(defresolver resolve-proposal-process [{:keys [db]} {::proposal/keys [id]}]
  {::pc/input #{::proposal/id}
   ::pc/output [::proposal/process]}
  (let [{:keys []} (log/spy :info (d/pull db [{::_proposals [::slug]}] [::proposal/id id]))]))

(defmutation add-proposal [{:keys [conn AUTH/user-id] :as env}
                           {::proposal/keys [id title body parents arguments]
                            ::keys [slug]
                            :or    {parents [] arguments []}}]
  {::pc/params    [::slug
                   ::proposal/id ::proposal/title ::proposal/body ::proposal/parents ::proposal/arguments]
   ::pc/output    [::proposal/id]
   ::pc/transform auth/check-logged-in}
  (let [real-id (proposal/new-proposal-id)
        tx-report (d/transact conn
                    [(proposal/tx-data-add #::proposal{:id real-id
                                                       :title title
                                                       :body body
                                                       :parents parents
                                                       :argument-idents arguments
                                                       :user-ident [:user/id user-id]})
                     [:db/add [::slug slug] ::proposals (str real-id)]])]
    {:tempids {id real-id}
     ::p/env  (assoc env :db (:db-after tx-report))
     ::proposal/id     real-id}))

(def resolvers
  [resolve-all-processes
   resolve-proposals
   resolve-proposal-process

   add-proposal])