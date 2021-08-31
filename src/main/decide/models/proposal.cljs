(ns decide.models.proposal
  (:require
    [com.fulcrologic.fulcro.raw.components :as rc]
    [decide.models.user :as user]))

(def Proposal
  (rc/nc [::id
          ::title
          ::body
          ::pro-votes ::con-votes
          ::created
          ::my-opinion-value
          {::parents '...}                                  ; this is a recursion
          {::original-author [::user/id ::user/display-name]}]
    {:componentName ::Proposal}))

(def approval-order (juxt ::pro-votes ::created))

(defmulti rank-by (fn [sort-order _] (keyword sort-order)))

(defmethod rank-by :old->new [_ proposals]
  (sort-by ::nice-id < proposals))

(defmethod rank-by :new->old [_ proposals]
  (sort-by ::nice-id > proposals))

(defmethod rank-by :most-approvals [_ proposals]
  (sort-by approval-order > proposals))

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