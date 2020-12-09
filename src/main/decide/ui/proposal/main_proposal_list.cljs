(ns decide.ui.proposal.main-proposal-list
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [decide.models.proposal :as proposal]
    [decide.ui.components.breadcrumbs :as breadcrumbs]
    [decide.ui.proposal.card :as proposal-card]
    [decide.ui.proposal.NewProposal :as new-proposal]
    [decide.utils :as utils]
    [material-ui.data-display :as dd]
    [material-ui.inputs :as inputs]
    [material-ui.layout :as layout]
    ["@material-ui/icons/Add" :default AddIcon]))

(defn add-proposal-fab [props]
  (let [extended? (utils/>=-breakpoint? "sm")]
    (layout/box
      {:position "fixed"
       :bottom   "16px"
       :right    "16px"}
      (inputs/fab
        (merge
          {:aria-label "Neuer Vorschlag"
           :title      "Neuer Vorschlag"
           :color      "secondary"
           :variant    (if extended? "extended" "round")}
          props)
        (comp/create-element AddIcon nil nil)
        (when extended?
          (layout/box {:ml 1}
            "Neuer Vorschlag"))))))

(defn empty-proposal-list-message []
  (layout/box {:p 2 :mx "auto"}
    (dd/typography {:align "center"
                    :color "textSecondary"}
      "Bisher gibt es keine Vorschläge.")))

(defsc MainProposalList [this {:keys [all-proposals]}]
  {:query         [{[:all-proposals '_] (comp/get-query proposal-card/Proposal)}]
   :ident         (fn [] [:content/id :main-proposal-list])
   :initial-state {:all-proposals []}
   :route-segment ["home"]
   :use-hooks?    true}
  (let [open-new-proposal-dialog (hooks/use-callback #(comp/transact! this [(new-proposal/show-new-proposal-form-dialog {})]))]
    (comp/fragment
      (layout/container {:maxWidth :md}
        (breadcrumbs/breadcrumb-nav
          [["Vorschläge" ""]])
        (if (empty? all-proposals)
          (empty-proposal-list-message)
          (layout/box {:pb 8 :clone true}                   ; to not cover up the FAB
            (dd/list {}
              (for [proposal all-proposals]
                (dd/list-item {:disableGutters true :key (::proposal/id proposal)}
                  (proposal-card/ui-proposal proposal)))))))

      (add-proposal-fab {:onClick open-new-proposal-dialog}))))