(ns decide.ui.proposal.main-proposal-list
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [decide.models.proposal :as proposal]
    [decide.ui.proposal.card :as proposal-card]
    [decide.ui.proposal.new-proposal :as new-proposal]
    [decide.utils.breakpoint :as breakpoint]
    [material-ui.data-display :as dd]
    [material-ui.inputs :as inputs]
    [material-ui.layout :as layout]
    ["@material-ui/icons/Add" :default AddIcon]
    [decide.models.process :as process]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [taoensso.timbre :as log]
    [com.fulcrologic.fulcro.data-fetch :as df]))

(defn add-proposal-fab [props]
  (let [extended? (breakpoint/>=? "sm")]
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

(defsc MainProposalList [this {::process/keys [slug proposals]
                               :ui/keys       [new-proposal-dialog]}]
  {:query         [::process/slug
                   {::process/proposals (comp/get-query proposal-card/Proposal)}
                   {:ui/new-proposal-dialog (comp/get-query new-proposal/NewProposalFormDialog)}]
   :ident         ::process/slug
   :route-segment ["decision" ::process/slug "proposals"]
   :will-enter    (fn [app {::process/keys [slug]}]
                    (let [ident (comp/get-ident MainProposalList {::process/slug slug})]
                      (dr/route-deferred ident
                        #(df/load! app ident MainProposalList
                           {:post-mutation        `dr/target-ready
                            :post-mutation-params {:target ident}}))))
   :pre-merge     (fn [{:keys [data-tree]}]
                    (let [slug (::process/slug data-tree)]
                      (merge data-tree
                        {:ui/new-proposal-dialog (comp/get-initial-state new-proposal/NewProposalFormDialog {:id slug})})))
   :use-hooks?    true}
  (let [open-new-proposal-dialog (hooks/use-callback #(comp/transact! this [(new-proposal/show {:id slug})]))]
    (comp/fragment
      (layout/container {:maxWidth :md}
        (dd/typography {:component "h2" :variant "h5"} slug)
        (if (empty? proposals)
          (empty-proposal-list-message)
          (layout/box {:pb 8 :clone true}                   ; to not cover up the FAB
            (dd/list {}
              (for [proposal proposals]
                (dd/list-item {:disableGutters true :key (::proposal/id proposal)}
                  (proposal-card/ui-proposal proposal {::process/slug slug})))))))

      (add-proposal-fab {:onClick open-new-proposal-dialog})
      (new-proposal/ui-new-proposal-form new-proposal-dialog))))