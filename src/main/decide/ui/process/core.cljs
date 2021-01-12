(ns decide.ui.process.core
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [decide.models.process :as process]
    [decide.ui.proposal.main-proposal-list :as proposal.main-list]
    [decide.ui.proposal.detail-page :as proposal.detail-page]
    [material-ui.data-display :as dd]
    [material-ui.inputs :as inputs]
    [material-ui.layout :as layout]
    [taoensso.timbre :as log]
    [decide.ui.proposal.new-proposal :as new-proposal]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.algorithms.merge :as mrg]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro.react.hooks :as hooks]))

(defsc ProcessHome [this {::process/keys [slug title description] :as props}]
  {:query [::process/slug ::process/title ::process/description]
   :ident ::process/slug
   :route-segment ["home"]
   :will-enter
   (fn will-enter-process-home [app {::process/keys [slug]}]
     (let [ident (comp/get-ident ProcessHome {::process/slug slug})]
       (dr/route-deferred ident
         (fn []
           (df/load! app ident ProcessHome
             {:post-mutation `dr/target-ready
              :post-mutation-params {:target ident}})))))}
  (layout/container {}
    #_(dd/typography {:component "h1" :variant "h2"} title)
    (dd/typography {:paragraph true} description)
    (inputs/button
      {:color "primary"
       :variant "contained"
       :href (str "/decision/" slug "/proposals")}
      "Zeige alle Vorschläge")
    (dom/code (str props))))

(defrouter ProcessRouter [_this _]
  {:router-targets [ProcessHome proposal.main-list/MainProposalList proposal.detail-page/ProposalPage]})

(def ui-process-router (comp/computed-factory ProcessRouter))

(defsc ProcessHeader [_ {::process/keys [slug title]}]
  {:query [::process/slug ::process/title ::process/description]
   :ident ::process/slug}
  (layout/box {}
    (dd/typography {:component "h1" :variant "h2"} title)
    (inputs/button
      {:color "secondary"
       :href (str "/decision/" slug "/home")}
      "Home")))

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
      :ui/new-proposal-dialog (comp/get-initial-state new-proposal/NewProposalFormDialog {:id slug})})
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
                         (dr/target-ready! app ident)
                         #_(df/load! app ident ProcessContext
                             {:post-mutation `dr/target-ready
                              :post-mutation-params {:target ident}}))))))
   #_:pre-merge #_(fn pre-merge-process [{:keys [data-tree]}]
                    (log/info "Pre-merge process")
                    (let [slug (::process/slug data-tree)]
                      (merge data-tree
                        (comp/get-initial-state ProcessContext {:slug slug}))))
   :use-hooks? true}
  (let [show-new-proposal-dialog (hooks/use-callback #(comp/transact! this [(new-proposal/show {:id slug})]))]
    (comp/fragment
      (when process-header
        (layout/container {}
          (ui-process-info process-header)))
      (ui-process-router process-router
        {:slug slug
         :show-new-proposal-dialog show-new-proposal-dialog})
      (new-proposal/ui-new-proposal-form new-proposal-dialog))))