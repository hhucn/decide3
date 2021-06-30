(ns decide.models.proposal
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [decide.models.user :as user]))

(defsc Author [_ _]
  {:query [::user/id ::user/display-name]
   :ident ::user/id})

(defsc Proposal [_this _props]
  {:query (fn []
            [::id
             ::title
             ::body
             ::pro-votes ::con-votes
             ::created
             ::my-opinion
             {::parents '...}                               ; this is a recursion
             {::original-author (comp/get-query Author)}])
   :ident ::id})

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
  (filter #(-> % ::my-opinion pos?) proposals))