(ns decide.ui.proposal.favorite-list
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.ui.proposal.card :as proposal-card]
    [decide.ui.proposal.new-proposal :as new-proposal]
    [decide.ui.proposal.plain-list :as plain-list]
    [decide.utils.breakpoint :as breakpoint]
    [mui.data-display :as dd]
    [mui.feedback.alert :as alert]
    [mui.inputs :as inputs]
    [mui.layout :as layout]
    [mui.layout.grid :as grid]
    [mui.transitions :as transitions]
    [decide.ui.components.flip-move :as flip-move]
    [com.fulcrologic.fulcro.dom :as dom]))

(defn line-divider [{:keys [label]}]
  (grid/item {:xs 12}
    (dd/divider {})
    (dd/typography {:variant :overline} label)))

(defn merge-alert [{:keys [message onNewProposal]}]
  (alert/alert
    {:severity :warning
     :action
     (inputs/button
       {:color :inherit
        :variant :outlined
        :size :small
        :onClick onNewProposal}
       (i18n/tr "Propose coalition"))}
    message))

(defn- show-merge-alert? [proposals]
  (let [winner (proposal/best proposals)
        my-proposals (proposal/my-approved proposals)]
    (or
      (< 1 (count my-proposals))                            ; if I have more than one approved proposals, one is guaranteed to lose.
      (not= winner (first my-proposals)))))


(defsc FavoriteList [this {::process/keys [slug proposals end-time]
                           :keys [process/features] :as props}]
  {:query [::process/slug
           {::process/proposals (comp/get-query proposal-card/ProposalCard)}
           ::process/end-time
           :process/features]
   :ident ::process/slug
   :use-hooks? true}
  (let [proposals (hooks/use-memo #(proposal/rank proposals) [proposals])
        [best-proposal & rest-proposals] proposals
        process-over? (process/over? props)
        process-running? (process/running? props)]
    (comp/fragment
      (line-divider {:label (i18n/tr "This is the best proposal for the moment")})

      (grid/item {:xs 12}
        (layout/container {:maxWidth :lg :disableGutters true}
          (layout/box {:pb 5}
            (when best-proposal
              (proposal-card/ui-proposal-card
                best-proposal
                {::process/slug slug
                 :process-over? (process/over? {::process/end-time end-time})
                 :features features
                 :card-props {:elevation 12}})))))

      ;; THOUGHT This could be sensible even with multiple approves. But what would the button do on click?
      (let [[my-proposal] (proposal/my-approved proposals)]
        ;; THOUGHT Maybe don't show, if you approve a coalition, that contains the current leader?
        (transitions/collapse {:in (show-merge-alert? proposals)}
          (merge-alert {:message (cond
                                   (contains? features :process.feature/single-approve)
                                   (i18n/tr "Your favourite proposal is not winning right now. Can you propose a coalition?")

                                   :else
                                   (i18n/tr "You approved proposals that aren't winning right now. Can you propose a coalition?"))
                        :onNewProposal #(comp/transact! this [(new-proposal/show {:parents [(comp/get-ident proposal-card/ProposalCard my-proposal)]})])})))

      (line-divider {:label (i18n/tr "All other proposals")})
      (let [proposal-cards
            (mapv
              #(proposal-card/ui-proposal-card % {::process/slug slug
                                                  :process-over? process-over?})
              rest-proposals)]
        (plain-list/plain-list {}
          (cond-> proposal-cards
            
            process-running?
            (conj (new-proposal/card {:disabled? (not (comp/shared this :logged-in?))
                                      :onClick #(comp/transact! this [(new-proposal/show {:slug slug})])}))))))))



(def ui-favorite-list (comp/computed-factory FavoriteList {:keyfn ::process/slug}))