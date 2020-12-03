(ns decide.models.opinion
  (:require
    [clojure.spec.alpha :as s]
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [datahike.api :as d]
    [datahike.core :as d.core]
    [decide.models.proposal :as proposal]
    [ghostwheel.core :refer [>defn =>]]))

(def schema [{:db/ident       :user/opinions
              :db/cardinality :db.cardinality/many
              :db/valueType   :db.type/ref
              :db/isComponent true}

             {:db/ident       ::proposal/opinions
              :db/cardinality :db.cardinality/many
              :db/valueType   :db.type/ref
              :db/isComponent true}

             {:db/ident       ::value
              :db/doc         "Value of alignment of the opinion. 0 is neutral. Should be -1, 0 or +1 for now."
              :db/cardinality :db.cardinality/one
              :db/valueType   :db.type/long}])

(s/def ::value #{-1 0 +1})

(>defn set-opinion! [conn user-id proposal-id opinion-value]
  [d.core/conn? :user/id ::proposal/id ::value => map?]
  (d/transact conn
    [[:db/add [::proposal/id proposal-id] ::proposal/opinions "temp"]
     [:db/add [:user/id user-id] :user/opinions "temp"]
     {:db/id  "temp"
      ::value opinion-value}]))

(defn pull-personal-opinion [db proposal user]
  (d/q
    '[:find ?value .
      :in $ ?proposal ?user
      :where
      [?proposal ::proposal/opinions ?opinions]
      [?user :user/opinions ?opinions]
      [?opinions ::value ?value]]
    db proposal user))

(defmutation add [{:keys [conn AUTH/user-id] :as env} {::proposal/keys [id]
                                                       :keys           [opinion]}]
  {::pc/params [::proposal/id :opinion]}
  (let [tx-report (set-opinion! conn user-id id opinion)]
    {::p/env (assoc env :db (:db-after tx-report))}))

(defresolver resolve-personal-opinion [{:keys [db AUTH/user-id]} {::proposal/keys [id]}]
  {::pc/input  #{::proposal/id}
   ::pc/output [::opinion]}
  (let [opinion-value
        (pull-personal-opinion db [::proposal/id id] [:user/id user-id])]
    {::proposal/opinion (or opinion-value 0)}))

(defresolver resolve-proposal-opinions [{:keys [db]} {::proposal/keys [id]}]
  {::pc/input  #{::proposal/id}
   ::pc/output [::proposal/pro-votes ::proposal/con-votes]}
  (let [opinions
        (d/q
          '[:find (clojure.core/frequencies ?values) .
            :in $ ?id
            :where
            [?e ::proposal/id ?id]
            [?e ::proposal/opinions ?opinions]
            [?opinions ::value ?values]]
          db id)]
    {::proposal/pro-votes (get opinions 1 0)
     ::proposal/con-votes (get opinions -1 0)}))

(def resolvers [resolve-personal-opinion resolve-proposal-opinions add])