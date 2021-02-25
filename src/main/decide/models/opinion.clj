(ns decide.models.opinion
  (:require
    [clojure.spec.alpha :as s]
    [com.fulcrologic.guardrails.core :refer [>defn =>]]
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [datahike.api :as d]
    [datahike.core :as d.core]
    [decide.models.authorization :as auth]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]))

(def schema [{:db/ident       ::user/opinions
              :db/cardinality :db.cardinality/many
              :db/valueType   :db.type/ref
              :db/isComponent true}

             {:db/ident       ::proposal/opinions
              :db/cardinality :db.cardinality/many
              :db/valueType :db.type/ref
              :db/isComponent true}

             {:db/ident ::value
              :db/doc "Value of alignment of the opinion. 0 is neutral. Should be -1, 0 or +1 for now."
              :db/cardinality :db.cardinality/one
              :db/valueType :db.type/long}])

(s/def ::value #{-1 0 +1})
(s/def ::opinion (s/keys :req [::value]))
(s/def ::proposal/opinions (s/coll-of ::opinion))

(>defn get-opinion [db user-ident proposal-ident]
  [d.core/db? ::user/lookup ::proposal/ident => (s/nilable nat-int?)]
  (d/q '[:find ?opinion .
         :in $ ?user ?proposal
         :where
         [?user ::user/opinions ?opinion]
         [?proposal ::proposal/opinions ?opinion]]
    db
    user-ident
    proposal-ident))

(>defn set-opinion! [conn user-ident proposal-ident opinion-value]
  [d.core/conn? ::user/lookup ::proposal/lookup ::value => map?]
  (if-let [opinion-id (get-opinion @conn user-ident proposal-ident)]
    (d/transact conn
      [{:db/id  opinion-id
        ::value opinion-value}])
    (d/transact conn
      [[:db/add proposal-ident ::proposal/opinions "temp"]
       [:db/add user-ident ::user/opinions "temp"]
       {:db/id  "temp"
        ::value opinion-value}])))

(>defn votes [{::proposal/keys [opinions] :as proposal}]
  [(s/keys :req [::proposal/opinions]) => (s/keys :req [::proposal/pro-votes ::proposal/con-votes])]
  (let [freqs (frequencies (map ::value opinions))]
    (assoc proposal
      ::proposal/pro-votes (get freqs 1 0)
      ::proposal/con-votes (get freqs -1 0))))

(defn get-values-for-proposal [db proposal-ident]
  (merge
    {1 0, -1 0}                                             ; default values
    (->> proposal-ident
      (d/pull db [{::proposal/opinions [::value]}])
      ::proposal/opinions
      (map :decide.models.opinion/value)
      frequencies)))

(defmutation add [{:keys [conn AUTH/user-id] :as env} {::proposal/keys [id]
                                                       :keys           [opinion]}]
  {::pc/params    [::proposal/id :opinion]
   ::pc/transform auth/check-logged-in}
  (let [tx-report (set-opinion! conn
                    [::user/id user-id]
                    [::proposal/id id]
                    opinion)]
    {::p/env (assoc env :db (:db-after tx-report))}))

(defresolver resolve-personal-opinion [{:keys [db AUTH/user-id]} {::proposal/keys [id]}]
  {::pc/input #{::proposal/id}
   ::pc/output [::proposal/my-opinion]}
  (when user-id
    (if-let [opinion (get-opinion db [::user/id user-id] [::proposal/id id])]
      (let [{::keys [value]} (d/pull db [[::value :default 0]] opinion)]
        {::proposal/my-opinion value})
      {::proposal/my-opinion 0})))


(defresolver resolve-proposal-opinions [{:keys [db]} {::proposal/keys [id]}]
  {::pc/input  #{::proposal/id}
   ::pc/output [::proposal/pro-votes ::proposal/con-votes]}
  (let [opinions (get-values-for-proposal db [::proposal/id id])]
    {::proposal/pro-votes (get opinions 1 0)
     ::proposal/con-votes (get opinions -1 0)}))

(def resolvers [resolve-personal-opinion resolve-proposal-opinions add])