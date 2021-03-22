(ns decide.models.proposal
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
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

(defn load-all! [app-or-comp]
  (df/load! app-or-comp :all-proposals Proposal))

(def approval-order (juxt ::pro-votes ::created))

(defmulti sort-proposals (fn [sort-order _] (keyword sort-order)))

(defmethod sort-proposals :old->new [_ proposals]
  (sort-by ::nice-id < proposals))

(defmethod sort-proposals :new->old [_ proposals]
  (sort-by ::nice-id > proposals))

(defmethod sort-proposals :most-approvals [_ proposals]
  (sort-by approval-order > proposals))

(defmethod sort-proposals :default [_ proposals]
  (sort-proposals :most-approvals proposals))

(def add-argument `add-argument)
