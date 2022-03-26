(ns decide.models.proposal
  (:require
    #?(:cljs    [cljs.spec.alpha :as s]
       :default [clojure.spec.alpha :as s])
    [com.fulcrologic.guardrails.core :refer [>def >defn => | <-]]
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

(>def ::id ::proposal/id)
(>def ::nice-id ::proposal/nice-id)
(>def ::title ::proposal/title)
(>def ::body ::proposal/body)
(>def ::created ::proposal/created)
(>def ::pro-votes ::proposal/pro-votes)
(>def ::con-votes ::proposal/con-votes)
(>def ::parents (s/coll-of (s/keys :req [::id]) :distinct true))
(>def ::proposal (s/keys :req [::id] :opt [::title ::body ::created ::parents ::pro-votes ::con-votes ::nice-id ::arguments]))

(>def ::ident (s/tuple #{::id} ::id))
(>def ::lookup (s/or :ident ::ident :db/id pos-int?))
(>def ::entity (s/and associative? #(contains? % :db/id)))

(>defn newest
  "Returns the newest proposal from a collection of `proposals`."
  [proposals]
  [(s/coll-of (s/keys :req [::created]) :min-count 1)
   => (s/keys :req [::created])
   | #(contains? (set proposals) %)]
  (->> proposals
    (sort-by ::created)
    reverse
    first))

(def approval-order (juxt ::pro-votes ::favorite-votes ::created))

(defmulti rank-by (fn [sort-order _] (keyword sort-order)))

(defmethod rank-by :old->new [_ proposals]
  (sort-by ::nice-id proposals))

(defmethod rank-by :new->old [_ proposals]
  (reverse (sort-by ::nice-id proposals)))

(defmethod rank-by :most-approvals [_ proposals]
  (reverse (sort-by approval-order proposals)))

(defmethod rank-by :default [_ proposals]
  (rank-by :most-approvals proposals))

(defn rank [proposals]
  (rank-by :most-approvals proposals))

(defn top-proposals
  "Returns all proposals that tie for first place, with no tie breaker in place."
  [proposals]
  (first (partition-by ::pro-votes (rank proposals))))

(defn best
  "Returns a single best proposal (i.e. winner)"
  [proposals]
  (first (rank proposals)))

(def add-argument `add-argument)

(defn my-approved [proposals]
  (filter #(-> % ::my-opinion-value pos?) proposals))
