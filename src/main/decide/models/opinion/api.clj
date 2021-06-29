(ns decide.models.opinion.api
  (:require
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [datahike.api :as d]
    [decide.models.authorization :as auth]
    [decide.models.opinion :as opinion]
    [decide.models.opinion.database :as opinion.db]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]))


(defmutation add [{:keys [conn AUTH/user-id] :as env} {::proposal/keys [id]
                                                       :keys [opinion]}]
  {::pc/params [::proposal/id :opinion]
   ::pc/transform auth/check-logged-in}
  (let [tx-report
        (d/transact conn
          {:tx-data
           (conj
             (opinion.db/->set @conn
               [::user/id user-id]
               [::proposal/id id]
               opinion)
             [:db/add "datomic.tx" :db/txUser [::user/id user-id]])})]
    {::p/env (assoc env :db (:db-after tx-report))}))

(defresolver resolve-personal-opinion [{:keys [db AUTH/user-id]} {::proposal/keys [id]}]
  {::pc/input #{::proposal/id}
   ::pc/output [::proposal/my-opinion]}
  (when user-id
    (if-let [opinion (opinion.db/get-opinion db [::user/id user-id] [::proposal/id id])]
      (let [{::opinion/keys [value]} (d/pull db [[::opinion/value :default 0]] opinion)]
        {::proposal/my-opinion value})
      {::proposal/my-opinion 0})))


(defresolver resolve-proposal-opinions [{:keys [db]} {::proposal/keys [id]}]
  {::pc/input  #{::proposal/id}
   ::pc/output [::proposal/pro-votes ::proposal/con-votes]}
  (let [opinions (opinion.db/get-values-for-proposal db [::proposal/id id])]
    {::proposal/pro-votes (get opinions 1 0)
     ::proposal/con-votes (get opinions -1 0)}))

(def resolvers [resolve-personal-opinion resolve-proposal-opinions add])