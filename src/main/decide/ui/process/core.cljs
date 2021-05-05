(ns decide.ui.process.core
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro.algorithms.merge :as mrg]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [decide.models.authorization :as auth]
    [decide.models.process :as process]
    [decide.routing :as r]
    [decide.ui.process.header :as process.header]
    [decide.ui.process.home :as process.home]
    [decide.ui.process.moderator-tab :as process.moderator]
    [decide.ui.process.personal-dashboard :as process.dashboard]
    [decide.ui.proposal.detail-page :as proposal.detail-page]
    [decide.ui.proposal.main-proposal-list :as proposal.main-list]
    [decide.ui.proposal.new-proposal :as new-proposal]
    [material-ui.layout :as layout]
    [material-ui.navigation.tabs :as tabs]
    [material-ui.surfaces :as surfaces]))

(defrouter ProcessRouter [_this _]
  {:router-targets
   [process.home/ProcessOverviewScreen
    proposal.main-list/MainProposalList
    proposal.detail-page/ProposalPage
    process.moderator/ProcessModeratorTab
    process.dashboard/PersonalProcessDashboard]})

(defn current-target [c]
  (-> (dr/route-target ProcessRouter (dr/current-route c ProcessRouter))
    (assoc :prefix (vec (take 2 (dr/current-route c))))))

(def ui-process-router (comp/computed-factory ProcessRouter))

(defn tab-bar [{current-target :target
                current-path :prefix} & targets]
  (let [targets (filter some? targets)
        lookup-map (zipmap (map :target targets) (range))
        current-index (get lookup-map current-target false)]
    (tabs/tabs
      {:value current-index
       :variant :scrollable
       :indicatorColor "secondary"
       :textColor "secondary"
       :component :nav}
      (for [{:keys [target] :as tab-props} targets
            :let [href (r/path->absolute-url (dr/into-path current-path target))]]
        (tabs/tab
          (merge (dissoc tab-props :target :path)
            {:href href
             :key href}))))))

(declare ProcessContext)

(defn process-already-loaded? [db ident]
  (boolean (get-in db ident)))

(defmutation set-current-process [{:keys [ident slug]}]
  (action [{:keys [app state]}]
    (let [process-ident [::process/slug slug]]
      (if (process-already-loaded? @state ident)
        (do
          (swap! state assoc :ui/current-process process-ident)
          (df/load! app process-ident process.header/Process
            {:target (targeting/multiple-targets
                       (targeting/replace-at (conj ident :process-header))
                       (targeting/replace-at [:ui/current-process])
                       (targeting/prepend-to [:all-processes])
                       (targeting/prepend-to [:root/all-processes]))
             :parallel true})
          (dr/target-ready! app ident))
        (do
          (mrg/merge-component! app ProcessContext (comp/get-initial-state ProcessContext {:slug slug}))
          (df/load! app process-ident process.header/Process
            {:target (targeting/multiple-targets
                       (targeting/replace-at (conj ident :process-header))
                       (targeting/replace-at [:ui/current-process])
                       (targeting/prepend-to [:all-processes])
                       (targeting/prepend-to [:root/all-processes]))
             :post-mutation `dr/target-ready
             :post-mutation-params {:target ident}}))))))


(defn current-process-already-set? [state ident]
  (= ident (get state :ui/current-process)))


(defsc ProcessContext [this {:ui/keys [process-router new-proposal-dialog]
                             :keys [ui/process-header root/current-session
                                    ui/current-process]}]
  {:query [{[:ui/current-process '_] (comp/get-query process.header/Process)}
           {:ui/process-header (comp/get-query process.header/ProcessHeader)}
           {:ui/process-router (comp/get-query ProcessRouter)}
           {:ui/new-proposal-dialog (comp/get-query new-proposal/NewProposalFormDialog)}
           {[:root/current-session '_] (comp/get-query auth/Session)}]
   :initial-state
   (fn [{:keys [slug]}]
     {:ui/process-router (comp/get-initial-state ProcessRouter)
      :ui/process-header (comp/get-initial-state process.header/ProcessHeader)
      :ui/new-proposal-dialog (comp/get-initial-state new-proposal/NewProposalFormDialog {:slug slug})})
   :ident (fn [] [:SCREEN ::ProcessContext])
   :route-segment ["decision" ::process/slug]
   :will-enter
   (fn will-enter-ProcessContext [app {slug ::process/slug}]
     (let [ident (comp/get-ident ProcessContext {})]
       (if (current-process-already-set? (app/current-state app) [::process/slug slug])
         (dr/route-immediate ident)
         (dr/route-deferred ident
           #(comp/transact! app [(set-current-process {:ident ident :slug slug})])))))
   :use-hooks? true}
  (let [{::process/keys [slug end-time moderators]} current-process
        show-new-proposal-dialog (hooks/use-callback #(comp/transact! this [(new-proposal/show {:slug slug})]))]
    (comp/fragment
      (surfaces/paper
        {:square true}
        (layout/container {:maxWidth :xl :disableGutters true}
          (process.header/ui-process-header process-header)
          (tab-bar (current-target this)
            {:label (i18n/trc "Overview over process" "Overview") :target process.home/ProcessOverviewScreen}
            {:label (i18n/tr "All proposals") :target proposal.main-list/MainProposalList}
            {:label "Dashboard" :target process.dashboard/PersonalProcessDashboard}
            (when (process/moderator? current-process (:user current-session))
              {:label (i18n/trc "Link to moderation page" "Moderation") :target process.moderator/ProcessModeratorTab}))))
      (ui-process-router process-router
        {:show-new-proposal-dialog show-new-proposal-dialog})
      (new-proposal/ui-new-proposal-form new-proposal-dialog))))