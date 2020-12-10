(ns decide.ui.proposal.core
  (:require
    [decide.ui.proposal.detail-page :as detail-page]
    [decide.ui.proposal.main-proposal-list :as proposal-list]))

(def router-targets [proposal-list/MainProposalList detail-page/ProposalPage])