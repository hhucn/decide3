(ns decide.models.proposal
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [decide.models.user :as user]))

(defsc Author [_ _]
  {:query [:user/id ::user/display-name]
   :ident :user/id})

(defsc Proposal [_this _props]
  {:query (fn []
            [::id
             ::title
             ::body
             ::pro-votes ::con-votes
             ::created
             ::opinion
             {::parents '...}                               ; this is a recursion
             {::original-author (comp/get-query Author)}])
   :ident ::id})

(defn load-all! [app-or-comp]
  (df/load! app-or-comp :all-proposals Proposal))

(def add-argument `add-argument)
