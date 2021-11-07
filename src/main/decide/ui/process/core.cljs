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
    [decide.routes :as routes]
    [decide.ui.process.header :as process.header]
    [decide.ui.process.home :as process.home]
    [decide.ui.process.moderator-tab :as process.moderator]
    [decide.ui.process.personal-dashboard :as process.dashboard]
    [decide.ui.proposal.detail-page :as proposal.detail-page]
    [decide.ui.proposal.main-proposal-list :as proposal.main-list]
    [decide.ui.proposal.new-proposal :as new-proposal]
    [mui.layout :as layout]
    [mui.navigation.tabs :as tabs]
    [mui.surfaces :as surfaces]))

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

(defsc TabBar [this props {:keys [targets]}]
  {:query [::process/slug
           [:com.fulcrologic.fulcro.ui-state-machines/asm-id ::ProcessRouter]]
   :initial-state {::process/slug :param/slug}}
  (let [{target :target
         prefix :prefix} (dr/route-target ProcessRouter (dr/current-route this ProcessRouter))
        current-path (:prefix (current-target this))
        targets (filter some? targets)
        lookup-map (zipmap (map :target targets) (range))   ; {"home" 0, "proposals" 1, ...}
        current-index (get lookup-map target false)]
    (tabs/tabs
      {:value current-index
       :variant :scrollable
       :indicatorColor "secondary"
       :textColor "secondary"
       :component :nav}
      (for [{:keys [target] :as tab-props} targets
            :let [href (routes/path->href (dr/into-path current-path target))]]
        (tabs/tab
          (merge (dissoc tab-props :target :path)
            {:href href
             :key href}))))))

(def ui-tab-bar (comp/computed-factory TabBar))

(declare ProcessContext)

(defn process-already-loaded? [db ident]
  (boolean (get-in db ident)))

(defmutation ensure-process-basics [{:keys [slug]}]
  (action [{:keys [app]}]
    (df/load! app [::process/slug slug] process/Basics)
    {:parallel true
     :target (targeting/prepend-to [:root/all-processes])}))

(defmutation set-current-process [{:keys [slug]}]
  (action [{:keys [app ref]}]
    (let [process-ident [::process/slug slug]
          process-target (targeting/multiple-targets
                           (targeting/replace-at (conj ref :ui/process-header))
                           (targeting/replace-at [:ui/current-process])
                           (targeting/prepend-to [:root/all-processes]))]
      (df/load! app process-ident process/Basics
        {:target process-target
         :post-mutation `dr/target-ready
         :post-mutation-params {:target ref}}))))

(defmutation leave-current-process [_]
  (action [{:keys [state]}]
    ;; THOUGHT Maybe keep the process, even if it is not on screen anymore?
    ;; This could allow us to present a "back to the decision" button.
    (swap! state assoc :ui/current-process nil)))

(defn current-process-already-set? [state ident]
  (= ident (get state :ui/current-process)))

(defn header-container [& children]
  (surfaces/paper
    {:square true}
    (apply layout/container {:maxWidth :xl :disableGutters true} children)))

(declare ProcessContext)

(defmutation init-process-context [{:keys [slug]}]
  (action [{:keys [state]}]
    (swap! state
      mrg/merge-component
      ProcessContext
      (comp/get-initial-state ProcessContext {:slug slug}))))

(defsc ProcessContext [this {:ui/keys [process-router new-proposal-dialog tab-bar]
                             :keys [ui/process-header root/current-session
                                    ui/current-process]}]
  {:ident (fn [] [:SCREEN ::ProcessContext])
   :query [::process/slug
           {[:ui/current-process '_] (comp/get-query process/Basics)}
           {:ui/process-header (comp/get-query process.header/ProcessHeader)}
           {:ui/process-router (comp/get-query ProcessRouter)}
           {:ui/new-proposal-dialog (comp/get-query new-proposal/NewProposalFormDialog)}
           {[:root/current-session '_] (comp/get-query auth/Session)}
           {:ui/tab-bar (comp/get-query TabBar)}]
   :initial-state
   (fn [{:keys [slug]}]
     (cond-> {:ui/process-router (comp/get-initial-state ProcessRouter)}
       slug (merge {::process/slug slug
                    :ui/tab-bar (comp/get-initial-state TabBar {:slug slug})
                    :ui/process-router (comp/get-initial-state ProcessRouter)
                    :ui/process-header (comp/get-initial-state process.header/ProcessHeader {:slug slug})
                    :ui/new-proposal-dialog (comp/get-initial-state new-proposal/NewProposalFormDialog {:slug slug})})))
   :route-segment (routes/segment ::routes/process-context)
   :will-enter
   (fn will-enter-ProcessContext [app {slug :process/slug}]
     (let [ident (comp/get-ident ProcessContext {::process/slug slug})]
       (if (current-process-already-set? (app/current-state app) [::process/slug slug])
         (dr/route-immediate ident)
         (dr/route-deferred ident
           #(comp/transact! app [(init-process-context {:slug slug})
                                 (set-current-process {:ident ident :slug slug})]
              {:ref ident})))))
   :use-hooks? true}
  (let [{::process/keys [slug]} current-process
        show-new-proposal-dialog (hooks/use-callback #(comp/transact! this [(new-proposal/show {:slug slug})]) [slug])]
    (comp/fragment
      (header-container
        (process.header/ui-process-header process-header)
        (ui-tab-bar tab-bar
          {:targets
           [{:label (i18n/trc "Overview over process" "Overview") :target process.home/ProcessOverviewScreen}
            {:label (i18n/tr "All proposals") :target proposal.main-list/MainProposalList}
            {:label (i18n/tr "Dashboard") :target process.dashboard/PersonalProcessDashboard}
            (when (process/moderator? current-process (:user current-session))
              {:label (i18n/trc "Link to moderation page" "Moderation") :target process.moderator/ProcessModeratorTab})]}))
      (ui-process-router process-router
        {:show-new-proposal-dialog show-new-proposal-dialog})
      (new-proposal/ui-new-proposal-form new-proposal-dialog))))