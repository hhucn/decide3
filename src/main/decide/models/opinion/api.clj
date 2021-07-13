(ns decide.models.opinion.api
  (:require
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [datahike.api :as d]
    [decide.models.authorization :as auth]
    [decide.models.opinion :as opinion]
    [decide.models.opinion.database :as opinion.db]
    [decide.models.process :as process]
    [decide.models.process.database :as process.db]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]))


(defmutation add [{:keys [conn db AUTH/user-id] :as env} {::proposal/keys [id]
                                                          :keys [opinion]}]
  {::pc/params [::proposal/id :opinion]
   ::pc/transform auth/check-logged-in}
  (let [proposal (d/entity db [::proposal/id id])
        process (::process/_proposals proposal)
        user (d/entity db [::user/id user-id])

        tx-report
        (d/transact conn
          {:tx-data
           (conj
             (process.db/->enter process user)
             (opinion.db/->set @conn
               user
               process
               proposal
               opinion)
             [:db/add "datomic.tx" :db/txUser [::user/id user-id]])})]
    {::p/env (assoc env :db (:db-after tx-report))}))

(defresolver resolve-personal-opinion [{:keys [db AUTH/user-id]} {::proposal/keys [id]}]
  {::pc/input #{::proposal/id}
   ::pc/output [::proposal/my-opinion]}
  (when user-id
    (let [user (d/entity db [::user/id user-id])
          proposal (d/entity db [::proposal/id id])
          opinion (opinion.db/get-opinion-eid db user proposal)]
      (if opinion
        (let [{::opinion/keys [value]} (d/pull db [[::opinion/value :default 0]] opinion)]
          {::proposal/my-opinion value})
        {::proposal/my-opinion 0}))))


(defresolver resolve-proposal-opinions [{:keys [db]} {::proposal/keys [id]}]
  {::pc/input #{::proposal/id}
   ::pc/output [::proposal/pro-votes ::proposal/con-votes]}
  (let [opinions (opinion.db/get-values-for-proposal db [::proposal/id id])]
    {::proposal/pro-votes (get opinions 1 0)
     ::proposal/con-votes (get opinions -1 0)}))

(defn- get-public-opinions [proposal]
  (let [features (set (get-in proposal [::process/_proposals :process/features] #{}))]
    (if (or true (contains? features :process.feature/voting.public))
      {::proposal/opinions
       (for [opinion (get proposal ::proposal/opinions)
             :when (not (zero? (::opinion/value opinion)))]
         {::opinion/value (::opinion/value opinion)
          ::opinion/user (select-keys (::user/_opinions opinion) [::user/id ::user/display-name])})}
      nil)))

(defresolver resolve-public-opinions [{:keys [db] :as env} {::proposal/keys [id]}]
  {::pc/input #{::proposal/id}
   ::pc/output [{::proposal/opinions [::opinion/value {::opinion/user [::user/id]}]}]}
  (let [proposal (d/entity db [::proposal/id id])]
    (get-public-opinions proposal)))

(def resolvers
  [resolve-personal-opinion
   resolve-proposal-opinions
   resolve-public-opinions

   add])