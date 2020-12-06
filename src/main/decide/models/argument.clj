(ns decide.models.argument
  (:require
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [datahike.api :as d]))

(def schema
  [{:db/ident       ::id
    :db/doc         "The id of the statement"
    :db/valueType   :db.type/uuid
    :db/unique      :db.unique/identity
    :db/cardinality :db.cardinality/one}

   {:db/ident       ::author
    :db/doc         "The user who authored the statement"
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one}

   {:db/ident       ::content
    :db/doc         "The string content of the comment"
    :db/valueType   :db.type/string
    ; :db/fulltext    true
    :db/cardinality :db.cardinality/one}])


(defresolver resolve-argument [{:keys [db]} {::keys [id]}]
  {::pc/input #{::id}
   ::pc/output [::content {::author [:user/id]}]}
  (d/pull db [::content {::author [:user/id]}] [::id id]))

(def resolvers
  [resolve-argument])
