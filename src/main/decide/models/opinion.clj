(ns decide.models.opinion
  (:require
    [clojure.spec.alpha :as s]
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [datahike.api :as d]
    [datahike.core :as d.core]
    [decide.models.proposal :as proposal]
    [ghostwheel.core :refer [>defn =>]]
    [taoensso.timbre :as log]))

(def schema [{:db/ident       :user/opinions
              :db/cardinality :db.cardinality/many
              :db/valueType   :db.type/ref
              :db/isComponent true}

             {:db/ident       ::proposal/opinions
              :db/cardinality :db.cardinality/many
              :db/valueType   :db.type/ref
              :db/isComponent true}

             {:db/ident       :opinion/value
              :db/doc         "Value of alignment of the opinion. 0 is neutral. Should be -1, 0 or +1 for now."
              :db/cardinality :db.cardinality/one
              :db/valueType   :db.type/long}])

(s/def :opinion/value #{-1 0 +1})

(>defn set-opinion! [conn user-id proposal-id opinion-value]
  [d.core/conn? :user/id ::proposal/id :opinion/value => map?]
  (d/transact conn
    [[:db/add [::proposal/id proposal-id] ::proposal/opinions "temp"]
     [:db/add [:user/id user-id] :user/opinions "temp"]
     {:db/id         "temp"
      :opinion/value opinion-value}]))

(defn pull-personal-opinion [db proposal user]
  (log/spy :debug
    (d/q
      '[:find ?value .
        :in $ ?proposal ?user
        :where
        [?proposal ::proposal/opinions ?opinions]
        [?user :user/opinions ?opinions]
        [?opinions :opinion/value ?value]]
      db proposal user)))

(defmutation add-opinion [{:keys [conn AUTH/user-id] :as env} {::keys [id]
                                                               :keys  [opinion]}]
  {::pc/params [::id :opinion]}
  (let [tx-report (set-opinion! conn user-id id opinion)]
    {::p/env (assoc env :db (:db-after tx-report))}))

(defresolver resolve-personal-opinion [{:keys [db AUTH/user-id]} {::keys [id]}]
  {::pc/input  #{::id}
   ::pc/output [::opinion]}
  (let [opinion-value
        (pull-personal-opinion db [::id id] [:user/id user-id])]
    {::proposal/opinion (or opinion-value 0)}))

(def resolver [resolve-personal-opinion add-opinion])