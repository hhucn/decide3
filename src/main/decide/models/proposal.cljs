(ns decide.models.proposal
  (:require
    [com.fulcrologic.fulcro.components :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.algorithms.merge :as mrg]
    [decide.models.user :as user]
    [com.fulcrologic.fulcro.data-fetch :as df]))

(defsc Proposal [_this _props]
  {:query (fn []
            [::id
             ::title
             ::body
             ::pro-votes ::con-votes
             ::created
             ::opinion
             {::parents '...}                               ; this is a recursion
             {::original-author [::user/display-name]}])    ; TODO replace join with real model
   :ident ::id})

(defn load-all! [app-or-comp]
  (df/load! app-or-comp :all-proposals Proposal))

(defmutation add-proposal [{::keys [_id _title _body _parents] :as params}]
  (action [{:keys [app]}]
    (mrg/merge-component! app Proposal params :append [:all-proposals]))
  (remote [env] (m/returning env Proposal)))

(defmutation add-opinion [{::keys [id]
                           :keys  [opinion]}]
  (action [{:keys [state]}]
    (swap! state update-in [::id id] assoc ::opinion opinion))
  (remote [env] true))