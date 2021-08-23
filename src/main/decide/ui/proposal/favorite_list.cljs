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
    [material-ui.data-display :as dd]
    [material-ui.inputs :as inputs]
    [material-ui.lab.alert :as alert]
    [material-ui.layout :as layout]
    [material-ui.layout.grid :as grid]
    [material-ui.transitions :as transitions]))

(defn line-divider [{:keys [label]}]
  (grid/item {:xs 12}
    (dd/divider {})
    (dd/typography {:variant :overline} label)))

(defn merge-alert [{:keys [onNewProposal]}]
  (alert/alert {:severity :warning
                :action
                (inputs/button
                  {:color :inherit
                   :variant :outlined
                   :onClick onNewProposal}
                  (i18n/tr "Propose coalition"))}
    (i18n/tr "Your favourite proposal is not winning right now. Can you propose a coalition?")))

(defn- show-merge-alert? [proposals]
  (let [my-proposal (->> proposals (filter #(-> % ::proposal/my-opinion-value pos?)) first)]
    (and my-proposal (not= (first proposals) my-proposal))))

(defsc FavoriteList [this {::process/keys [slug proposals end-time]
                           :keys [process/features]}]
  {:query [::process/slug
           {::process/proposals (comp/get-query proposal-card/ProposalCard)}
           ::process/end-time
           :process/features]
   :ident ::process/slug
   :use-hooks? true}
  (let [proposals (hooks/use-memo #(proposal/rank proposals) [proposals])
        [best-proposal & rest-proposals] proposals
        >=-sm? (breakpoint/>=? "sm")]
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
      (when (contains? features :process.feature/single-approve)
        (let [[my-proposal] (proposal/my-approved proposals)]
          ;; THOUGHT Maybe don't show, if you approve a coalition, that contains the current leader?
          (transitions/collapse {:in (show-merge-alert? proposals)}
            (merge-alert {:onNewProposal #(comp/transact! this [(new-proposal/show {:parents [(comp/get-ident proposal-card/ProposalCard my-proposal)]})])}))))

      (line-divider {:label (i18n/tr "All other proposals")})
      (plain-list/plain-list
        {:items
         (map #(comp/computed % {::process/slug slug
                                 :process-over? (process/over? {::process/end-time end-time})
                                 :features features
                                 :variant (when >=-sm? :outlined)})
           rest-proposals)}))))

(def ui-favorite-list (comp/computed-factory FavoriteList {:keyfn ::process/slug}))