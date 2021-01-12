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
    [material-ui.layout.grid :as grid]
    ["@material-ui/icons/Add" :default AddIcon]
    [decide.models.process :as process]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [taoensso.timbre :as log]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [material-ui.navigation :as navigation]))


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

(defsc MainProposalList [this {::process/keys [slug title proposals]
                               :ui/keys [new-proposal-dialog]}]
  {:query [::process/slug
           {::process/proposals (comp/get-query proposal-card/ProposalCard)}]
   :ident ::process/slug
   :route-segment ["decision" ::process/slug "proposals"]
   :will-enter (fn [app {::process/keys [slug]}]
                 (let [ident (comp/get-ident MainProposalList {::process/slug slug})]
                   (dr/route-deferred ident
                     #(df/load! app ident MainProposalList
                        {:post-mutation        `dr/target-ready
                         :post-mutation-params {:target ident}}))))
   :use-hooks? true}
  (comp/fragment
    (layout/container {:maxWidth :xl}
      (layout/box {:pb 8 :clone true}
        (grid/container {:spacing 2 :alignItems "stretch"}
          (for [{id ::proposal/id :as proposal} proposals]
            (grid/item {:xs 12 :md 6 :lg 4 :xl 3 :key id}
              (proposal-card/ui-proposal-card proposal {::process/slug slug}))))))

      (add-proposal-fab {:onClick open-new-proposal-dialog})
      (new-proposal/ui-new-proposal-form new-proposal-dialog))))