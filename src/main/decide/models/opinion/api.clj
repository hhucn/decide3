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
    [decide.models.user :as user]
    [decide.server-components.database :as db]))


(defmutation add [{:keys [conn db AUTH/user] :as env} {::proposal/keys [id]
                                                       :keys [opinion]}]
  {::pc/params [::proposal/id :opinion]
   ::pc/output [::proposal/id ::process/slug]
   ::pc/transform auth/check-logged-in}
  (let [proposal (d/entity db [::proposal/id id])
        process (::process/_proposals proposal)

        tx-report
        (db/transact-as conn user
          {:tx-data
           (vec
             (concat
               (process.db/->enter process user)
               (opinion.db/->set @conn
                 user
                 process
                 proposal
                 opinion)))})]
    {::proposal/id id
     ::process/slug (::process/slug process)
     ::p/env (assoc env :db (:db-after tx-report))}))

(defresolver resolve-personal-opinion-value [{:keys [db AUTH/user]} {::proposal/keys [id]}]
  {::pc/input #{::proposal/id}
   ::pc/output [{::proposal/my-opinion [::opinion/value]}
                ::proposal/my-opinion-value]}
  (when user
    (when-let [proposal (d/entity db [::proposal/id id])]
      (let [opinion (opinion.db/get-opinion db user proposal)]
        {::proposal/my-opinion (update (select-keys opinion #{::opinion/value}) ::opinion/value #(or % 0))
         ::proposal/my-opinion-value (get opinion ::opinion/value 0)}))))


(defresolver resolve-proposal-opinions [{:keys [db]} {::proposal/keys [id]}]
  {::pc/input #{::proposal/id}
   ::pc/output [::proposal/pro-votes ::proposal/con-votes ::proposal/favorite-votes]}
  (let [opinions (opinion.db/get-values-for-proposal db [::proposal/id id])]
    {::proposal/pro-votes (+ (get opinions 1 0) (get opinions 2 0))
     ::proposal/favorite-votes (get opinions 2 0)
     ::proposal/con-votes (get opinions -1 0)}))

(defn- get-public-opinions [proposal]
  (let [process (::process/_proposals proposal)]
    (when (process/public-voting? process)
      {::proposal/opinions
       (for [opinion (get proposal ::proposal/opinions)
             :when (not (zero? (::opinion/value opinion)))]
         {::opinion/value (::opinion/value opinion)
          ::opinion/user (select-keys (::user/_opinions opinion) [::user/id ::user/display-name])})})))

(defresolver resolve-public-opinions [{:keys [db]} {::proposal/keys [id]}]
  {::pc/input #{::proposal/id}
   ::pc/output [{::proposal/opinions [::opinion/value {::opinion/user [::user/id]}]}]}
  (let [proposal (d/entity db [::proposal/id id])]
    (get-public-opinions proposal)))

(def resolvers
  [resolve-personal-opinion-value
   resolve-proposal-opinions
   resolve-public-opinions

   add])