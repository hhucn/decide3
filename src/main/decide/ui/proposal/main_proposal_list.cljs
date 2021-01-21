(ns decide.ui.proposal.main-proposal-list
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.ui.proposal.card :as proposal-card]
    [decide.utils.breakpoint :as breakpoint]
    [material-ui.data-display :as dd]
    [material-ui.inputs :as inputs]
    [material-ui.layout :as layout]
    [material-ui.layout.grid :as grid]
    [material-ui.surfaces :as surfaces]
    ["@material-ui/icons/Add" :default AddIcon]))


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

(defmulti sort-proposals (fn [sort-order _] (keyword sort-order)))

(defmethod sort-proposals :old->new [_ proposals]
  (sort-by ::proposal/nice-id < proposals))

(defmethod sort-proposals :new->old [_ proposals]
  (sort-by ::proposal/nice-id > proposals))

(defmethod sort-proposals :most-approvals [_ proposals]
  (sort-by ::proposal/pro-votes > proposals))

(defmethod sort-proposals :default [_ proposals]
  (sort-proposals "old->new" proposals))


(defsc MainProposalList [this {::process/keys [slug proposals]} {:keys [show-new-proposal-dialog]}]
  {:query [::process/slug
           {::process/proposals (comp/get-query proposal-card/ProposalCard)}]
   :ident ::process/slug
   :route-segment ["proposals"]
   :will-enter (fn [app {::process/keys [slug]}]
                 (let [ident (comp/get-ident MainProposalList {::process/slug slug})]
                   (dr/route-deferred ident
                     #(df/load! app ident MainProposalList
                        {:post-mutation `dr/target-ready
                         :post-mutation-params {:target ident}}))))
   :use-hooks? true}
  (let [[selected-sort set-selected-sort!] (hooks/use-state "new->old")
        sorted-proposals (sort-proposals selected-sort proposals)]
    (comp/fragment
      (layout/container {:maxWidth :xl}
        (surfaces/toolbar {}
          (dd/typography {} "Sortieren Nach:")
          (inputs/native-select {:value selected-sort
                                 :variant "outlined"
                                 :onChange (fn [e]
                                             (set-selected-sort! (evt/target-value e)))}
            (dom/option {:value "new->old"} "Neu → Alt")
            (dom/option {:value "old->new"} "Alt → Neu")
            (dom/option {:value "most-approvals"} "Zustimmungen ↓")))
        (layout/box {:pb 8 :clone true}
          (grid/container {:spacing 2 :alignItems "stretch"}
            (for [{id ::proposal/id :as proposal} sorted-proposals]
              (grid/item {:xs 12 :md 6 :lg 4 :xl 3 :key id}
                (proposal-card/ui-proposal-card proposal {::process/slug slug}))))))

      (add-proposal-fab {:onClick show-new-proposal-dialog}))))