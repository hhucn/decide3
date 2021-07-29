(ns decide.ui.proposal.lists.hierarchy
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.ui.proposal.card :as proposal-card]
    [material-ui.data-display :as dd]
    [material-ui.layout.grid :as grid]))

(defsc Parent
  "Item on the right side"
  [this {:keys [>/card]} {:keys [card-props]}]
  {:query [::proposal/id
           {:>/card (comp/get-query proposal-card/ProposalCard)}]
   :ident ::proposal/id}
  (grid/item {:xs 12 :sm 6 :md 4}
    (proposal-card/ui-proposal-card card card-props)))

(def ui-parent (comp/computed-factory Parent {:keyfn ::proposal/id}))

(defsc ProposalRow
  "One row"
  [this {::proposal/keys [parents]
         :keys [>/card]}
   {:keys [card-props] :as computed}]
  {:query [::proposal/id
           {:>/card (comp/get-query proposal-card/ProposalCard)}

           {::proposal/parents (comp/get-query Parent)}

           ;; For sorting... ;; THOUGHT (I have to do something about the sorting situation, this is not sustainable)
           ::proposal/pro-votes
           ::proposal/nice-id
           ::proposal/created]
   :ident ::proposal/id}
  (grid/container {:xs 12 :style {:flexGrow 1} :item true :spacing 1}
    (grid/item {:xs 12 :lg 4}
      (proposal-card/ui-proposal-card card (merge card-props {:elevation 10})))

    (grid/item {:xs 12 :lg 8}
      (dd/typography {:variant :overline} (i18n/tr "Based on"))
      (grid/container {:item true :spacing 1 :direction :row}
        (if (empty? parents)
          (grid/item {:xs 12 :sm 6 :md 4} (dd/typography {:variant :body2 :color :textSecondary} (i18n/tr "Nothing")))
          (mapv #(ui-parent % computed) parents))))))


(def ui-proposal-row (comp/computed-factory ProposalRow {:keyfn ::proposal/id}))

(defsc HierarchyList [this {::process/keys [slug proposals]
                            :keys [process/features]}
                      {:keys [sort-order process-over?]
                       :or {sort-order :most-approvals
                            process-over? false}}]
  {:query [::process/slug
           :process/features
           {::process/proposals (comp/get-query ProposalRow)}]
   :ident ::process/slug}
  (grid/container {:spacing 5}
    (->> proposals
      (proposal/rank-by sort-order)
      (mapv #(ui-proposal-row % {:card-props {::process/slug slug
                                              :process-over? process-over?
                                              :features features}})))))


(def ui-hierarchy-list (comp/computed-factory HierarchyList))