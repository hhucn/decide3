(ns decide.models.proposal
  (:require
    [clojure.spec.alpha :as s]
    [com.fulcrologic.guardrails.core :refer [>defn => | <-]]
    [decide.proposal :as proposal]
    [decide.specs.proposal]))

(def schema
  [{:db/ident ::id
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity
    :db/valueType :db.type/uuid}

   {:db/ident ::nice-id
    :db/cardinality :db.cardinality/one
    :db/valueType :db.type/long}

   {:db/ident ::title
    :db/doc "The short catchy title of a proposal."
    :db/cardinality :db.cardinality/one
    ; :db/fulltext    true
    :db/valueType :db.type/string}

   {:db/ident ::body
    :db/doc "A descriptive body of the proposal."
    :db/cardinality :db.cardinality/one
    ; :db/fulltext    true
    :db/valueType :db.type/string}

   {:db/ident ::created
    :db/doc "When the proposal was created."
    :db/cardinality :db.cardinality/one
    :db/valueType :db.type/instant}

   {:db/ident ::original-author
    :db/doc "The user who proposed the proposal."
    :db/cardinality :db.cardinality/one
    :db/valueType :db.type/ref}

   {:db/ident ::parents
    :db/doc "≥0 parent proposals from which the proposal is derived."
    :db/cardinality :db.cardinality/many
    :db/valueType :db.type/ref}

   {:db/ident ::arguments
    :db/cardinality :db.cardinality/many
    :db/valueType :db.type/ref}])

(s/def ::id ::proposal/id)
(s/def ::nice-id ::proposal/nice-id)
(s/def ::title ::proposal/title)
(s/def ::body ::proposal/body)
(s/def ::created ::proposal/created)
(s/def ::pro-votes ::proposal/pro-votes)
(s/def ::con-votes ::proposal/con-votes)
(s/def ::parents (s/coll-of (s/keys :req [::id]) :distinct true))
(s/def ::proposal (s/keys :req [::id] :opt [::title ::body ::created ::parents ::pro-votes ::con-votes ::nice-id ::arguments]))

(s/def ::ident (s/tuple #{::id} ::id))
(s/def ::lookup (s/or :ident ::ident :db/id pos-int?))
(s/def ::entity (s/and associative? #(contains? % :db/id)))

