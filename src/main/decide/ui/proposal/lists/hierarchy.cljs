(ns decide.ui.proposal.lists.hierarchy
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.ui.proposal.card :as proposal-card]
    [material-ui.data-display :as dd]
    [material-ui.layout.grid :as grid]))

(defsc Child
  "Item on the right side"
  [this {:keys [>/card]}]
  {:query [::proposal/id
           {:>/card (comp/get-query proposal-card/ProposalCard)}]
   :ident ::proposal/id}
  (grid/item {:xs 12 :sm 6 :md 4}
    (proposal-card/ui-proposal-card card)))

(def ui-child (comp/factory Child {:keyfn ::proposal/id}))

(defsc ProposalRow
  "One row"
  [this {::proposal/keys [children]
         :keys [>/card]}]
  {:query [::proposal/id
           {:>/card (comp/get-query proposal-card/ProposalCard)}
           {::proposal/children (comp/get-query Child)}]
   :ident ::proposal/id}
  (grid/container {:xs 12 :style {:flexGrow 1} :item true :spacing 1}
    (grid/item {:xs 12 :lg 4}
      (proposal-card/ui-proposal-card card {:elevation 10}))

    (grid/item {:xs 12 :lg 8}
      (dd/typography {:variant :overline} (i18n/tr "Based on"))
      (grid/container {:item true :spacing 1 :direction :row}
        (mapv ui-child children)))))


(def ui-proposal-row (comp/factory ProposalRow {:keyfn ::proposal/id}))

(defsc HierarchyList [this {::process/keys [proposals]}]
  {:query [::process/slug
           {::process/proposals (comp/get-query ProposalRow)}]
   :ident ::process/slug}
  (grid/container {:spacing 5}
    (mapv ui-proposal-row proposals)))

(def ui-hierarchy-list (comp/factory HierarchyList))