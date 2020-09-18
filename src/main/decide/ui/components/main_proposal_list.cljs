(ns decide.ui.components.main-proposal-list
  (:require [decide.models.proposal :as proposal]
            [material-ui.data-display :as dd]
            [com.fulcrologic.fulcro.react.hooks :as hooks]
            [decide.ui.components.NewProposal :as new-proposal]
            [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [material-ui.layout :as layout]
            [decide.utils :as utils]
            [decide.ui.components.breadcrumbs :as breadcrumbs]
            [material-ui.inputs :as inputs]
            ["@material-ui/icons/Add" :default AddIcon]
            ["React" :as react]))

(defn add-proposal-fab [props]
  (layout/box
    {:position "fixed"
     :bottom   "16px"
     :right    "16px"}
    (inputs/fab
      (merge
        {:aria-label "Neuer Vorschlag"
         :title      "Neuer Vorschlag"
         :color      "secondary"
         :variant    (if (utils/>=-breakpoint? "sm") "extended" "round")}
        props)
      (react/createElement AddIcon)
      (when (utils/>=-breakpoint? "sm")
        (layout/box {:ml 1}
          "Neuer Vorschlag")))))

(defn empty-proposal-list-message []
  (layout/box {:p 2 :mx "auto"}
    (dd/typography {:align "center"
                    :color "textSecondary"}
      "Bisher gibt es keine Vorschläge.")))

(defsc MainProposalList [this {:keys [all-proposals]}]
  {:query         [{[:all-proposals '_] (comp/get-query proposal/Proposal)}]
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
                (dd/list-item {:disableGutters true :key (:proposal/id proposal)}
                  (proposal/ui-proposal proposal)))))))

      (add-proposal-fab {:onClick open-new-proposal-dialog}))))