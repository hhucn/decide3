(ns decide.models.proposal
  (:require
    [com.fulcrologic.fulcro.components :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.algorithms.merge :as mrg]
    [decide.models.user :as user]))

(defsc Proposal [_this _props]
  {:query (fn []
            [:proposal/id
             :proposal/title
             :proposal/body
             :proposal/pro-votes :proposal/con-votes
             :proposal/created
             :proposal/opinion
             {:proposal/parents '...}                       ; this is a recursion
             {:proposal/original-author [::user/display-name]}]) ; TODO replace join with real model
   :ident :proposal/id})

(defmutation add-proposal [{:proposal/keys [_id _title _body _parents] :as params}]
  (action [{:keys [app]}]
    (mrg/merge-component! app Proposal params :append [:all-proposals]))
  (remote [env] (m/returning env Proposal)))

(defmutation add-opinion [{:keys [proposal/id opinion]}]
  (action [{:keys [state]}]
    (swap! state update-in [:proposal/id id] assoc :proposal/opinion opinion))
  (remote [env] true))