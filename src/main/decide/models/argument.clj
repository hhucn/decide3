(ns decide.models.argument
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [com.fulcrologic.guardrails.core :refer [>defn =>]]
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [datahike.api :as d]
    [datahike.core :as d.core]))

(def schema
  [{:db/ident       ::id
    :db/doc "The id of the statement"
    :db/valueType :db.type/uuid
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one}

   {:db/ident ::author
    :db/doc "The user who authored the statement"
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}

   {:db/ident ::content
    :db/doc "The string content of the comment"
    :db/valueType :db.type/string
    ; :db/fulltext    true
    :db/cardinality :db.cardinality/one}

   {:db/ident ::pro?
    :db/valueType :db.type/boolean
    :db/cardinality :db.cardinality/one}])

(s/def ::id uuid?)
(s/def ::ident (s/tuple #{::id} ::id))
(s/def ::content (s/and string? (complement str/blank?)))

(>defn tx-map [{:keys [::content author ::pro?]}]
  [(s/keys :req [::content]) => (s/keys :req [::id ::content])]
  {::id (d.core/squuid)
   ::content content
   ::author author
   ::pro? pro?})

(defresolver resolve-argument [{:keys [db]} {::keys [id]}]
  {::pc/input #{::id}
   ::pc/output [::content ::pro? {::author [:decide.models.user/id]}]}
  (d/pull db [::content ::pro? {::author [:decide.models.user/id]}] [::id id]))

(def resolvers
  [resolve-argument])
