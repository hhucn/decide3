(ns decide.ui.process.core
  (:require
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro.algorithms.merge :as mrg]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [decide.models.process :as process]
    [decide.ui.process.home :as process.home]
    [decide.ui.proposal.detail-page :as proposal.detail-page]
    [decide.ui.proposal.main-proposal-list :as proposal.main-list]
    [decide.ui.proposal.new-proposal :as new-proposal]
    [material-ui.data-display :as dd]
    [material-ui.layout :as layout]
    [material-ui.navigation.tabs :as tabs]
    [material-ui.surfaces :as surfaces]))

(defrouter ProcessRouter [_this _]
  {:router-targets [process.home/ProcessHome proposal.main-list/MainProposalList proposal.detail-page/ProposalPage]})

(def ui-process-router (comp/computed-factory ProcessRouter))

(defsc ProcessHeader [_ {::process/keys [title]}]
  {:query [::process/slug ::process/title]
   :ident ::process/slug}
  (layout/box {:mx 2 :mb 2 :mt 0}
    (dd/typography {:component "h1" :variant "h2"} title)))

(def ui-process-info (comp/factory ProcessHeader))

(defsc ProcessContext [this {:ui/keys [process-router new-proposal-dialog]
                             :keys [process-header ::process/slug]}]
  {:query [::process/slug
           {:process-header (comp/get-query ProcessHeader)}
           {:ui/process-router (comp/get-query ProcessRouter)}
           {:ui/new-proposal-dialog (comp/get-query new-proposal/NewProposalFormDialog)}]
   :initial-state
   (fn [{:keys [slug]}]
     {::process/slug slug
      :ui/process-router (comp/get-initial-state ProcessRouter)
      :ui/new-proposal-dialog (comp/get-initial-state new-proposal/NewProposalFormDialog {:slug slug})})
   :ident [:process-context ::process/slug]
   :route-segment ["decision" ::process/slug]
   :will-enter (fn [app {slug ::process/slug}]
                 (let [ident (comp/get-ident ProcessContext {::process/slug slug})]
                   (if (get-in (app/current-state app) ident)
                     (dr/route-immediate ident)
                     (dr/route-deferred ident
                       (fn []
                         (mrg/merge-component! app ProcessContext (comp/get-initial-state ProcessContext {:slug slug}))
                         (df/load! app [::process/slug slug] ProcessHeader
                           {:target (targeting/replace-at (conj ident :process-header))})
                         (dr/target-ready! app ident))))))
   :use-hooks? true}
  (let [show-new-proposal-dialog (hooks/use-callback #(comp/transact! this [(new-proposal/show {:slug slug})]))]
    (comp/fragment
      (surfaces/paper
        {:square true}
        (layout/container {:maxWidth :xl :disableGutters true}
          (when process-header
            (ui-process-info process-header))
          (let [current-target (case (first (drop 2 (dr/current-route this)))
                                 "home" 0
                                 "proposals" 1
                                 false)]
            (tabs/tabs {:value current-target
                        :indicatorColor "secondary"
                        :textColor "secondary"}
              (tabs/tab {:label "Home"
                         :href (str "/decision/" slug "/home")})
              (tabs/tab {:label "Alle Vorschläge"
                         :href (str "/decision/" slug "/proposals")})))))
      (ui-process-router process-router
        {:slug slug
         :show-new-proposal-dialog show-new-proposal-dialog})
      (new-proposal/ui-new-proposal-form new-proposal-dialog))))