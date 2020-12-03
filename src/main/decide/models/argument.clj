(ns decide.models.argument
  (:require
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [datahike.api :as d]
    [datahike.core :as d.core]
    [decide.models.proposal :as proposal]))

(def schema
  [{:db/ident       ::id
    :db/doc         "The id of the statement"
    :db/valueType   :db.type/uuid
    :db/unique      :db.unique/identity
    :db/cardinality :db.cardinality/one}

   {:db/ident ::author
    :db/doc         "The user who authored the statement"
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}

   {:db/ident       ::content
    :db/doc         "The string content of the comment"
    :db/valueType   :db.type/string
    ; :db/fulltext    true
    :db/cardinality :db.cardinality/one}

   {:db/ident       ::proposal/arguments
    :db/cardinality :db.cardinality/many
    :db/valueType   :db.type/ref}])


(defresolver resolve-argument [{:keys [db]} {::keys [id]}]
  {::pc/input #{::id}
   ::pc/output [::content {::author [:user/id]}]}
  (d/pull db [::content {::author [:user/id]}] [::id id]))

(defmutation add-argument [{:keys [conn AUTH/user-id] :as env} {::proposal/keys [id]
                                                                :keys           [temp-id content]}]
  {::pc/output [::id]}
  (let [real-id (d.core/squuid)
        statement {:db/id    "temp"
                   ::id      real-id
                   ::content content
                   ::author  [:user/id user-id]}
        tx-report (d/transact conn
                    [statement
                     [:db/add [::proposal/id id] ::proposal/arguments "temp"]])]
    {:tempids     {temp-id real-id}
     ::p/env      (assoc env :db (:db-after tx-report))
     ::id real-id}))

(defresolver resolve-arguments [{:keys [db]} {::proposal/keys [id]}]
  {::pc/input  #{::proposal/id}
   ::pc/output [{::proposal/arguments [::id]}]}
  (d/pull db [{::proposal/arguments [::id]}] [::proposal/id id]))

(def resolvers [add-argument resolve-argument resolve-arguments])
