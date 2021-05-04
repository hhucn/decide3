(ns decide.models.proposal
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [com.fulcrologic.guardrails.core :refer [>defn => | <-]]
    [datahike.core :as d.core])
  (:import (java.util Date)))

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

(s/def ::id uuid?)
(s/def ::nice-id pos-int?)
(s/def ::title (s/and string? (complement str/blank?)))
(s/def ::body string?)
(s/def ::created inst?)
(s/def ::pro-votes nat-int?)
(s/def ::con-votes nat-int?)
(s/def ::parents (s/coll-of (s/keys :req [::id]) :distinct true))
(s/def ::proposal (s/keys :req [::id] :opt [::title ::body ::created ::parents ::pro-votes ::con-votes ::nice-id ::arguments]))

(s/def ::ident (s/tuple #{::id} ::id))
(s/def ::lookup (s/or :ident ::ident :db/id pos-int?))


(defn tx-map [{::keys [id nice-id title body parents argument-idents created original-author]
               :or {parents []
                    argument-idents []}}]
  [(s/keys :req [::title ::body ::nice-id]
     :opt [::id])
   => (s/keys :req [::id ::title ::nice-id ::body ::created])]
  (let [created (or created (Date.))]
    {::id (or id (d.core/squuid (inst-ms created)))
     ::title title
     ::nice-id nice-id
     ::body body
     ::parents parents
     ::arguments argument-idents                            ; TODO check if arguments exist and belog to parents
     ::original-author original-author
     ::created created}))

(defn ->add [process-lookup {::keys [id nice-id title body parents argument-idents created original-author]
                             :or {parents []
                                  argument-idents []}}]
  (let [created (or created (Date.))
        id (or id (d.core/squuid (inst-ms created)))]
    [{:db/id (str id)
      ::id id
      ::title title
      ::nice-id nice-id
      ::body body
      ::parents parents
      ::arguments argument-idents                           ; TODO check if arguments exist and belog to parents
      ::original-author original-author
      ::created created}
     [:db/add process-lookup :decide.models.process/proposals (str id)]]))
