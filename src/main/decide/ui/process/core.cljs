(ns decide.ui.process.core
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro.algorithms.merge :as mrg]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [decide.models.authorization :as auth]
    [decide.models.process :as process]
    [decide.models.user :as user]
    [decide.routing :as r]
    [decide.utils.time :as time]
    [decide.ui.process.home :as process.home]
    [decide.ui.process.moderator-tab :as process.moderator]
    [decide.ui.proposal.detail-page :as proposal.detail-page]
    [decide.ui.proposal.main-proposal-list :as proposal.main-list]
    [decide.ui.process.personal-dashboard :as process.dashboard]
    [decide.ui.proposal.new-proposal :as new-proposal]
    [material-ui.data-display :as dd]
    [material-ui.lab.alert :as alert]
    [material-ui.layout :as layout]
    [material-ui.navigation.tabs :as tabs]
    [material-ui.surfaces :as surfaces]
    [material-ui.transitions :as transitions]
    [material-ui.inputs :as inputs]
    ["@material-ui/icons/ExpandMore" :default ExpandMore]
    ["@material-ui/icons/ExpandLess" :default ExpandLess]))

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

(defsc Moderator [_ _]
  {:query [::user/id]
   :ident ::user/id})

(defn process-ends-alert [{:keys [component]}]
  (alert/alert {:severity :success}
    (i18n/trf "Ended on the {endDatetime}!" {:endDatetime component})))

(defn process-ended-alert [{:keys [component]}]
  (alert/alert {:severity :info}
    (i18n/trf "Ends at {endDatetime}" {:endDatetime component})))

(defn description-collapse [{:keys [open? description toggle!]}]
  (comp/fragment
    (transitions/collapse {:in open?}
      (dd/typography {:variant :body1
                      :style {:whiteSpace :pre-line}}
        description))
    (inputs/button {:variant :outlined
                    :size :small
                    :onClick #(toggle! (not open?))
                    :endIcon (if open?
                               (dom/create-element ExpandLess)
                               (dom/create-element ExpandMore))}
      "Details")))


(defsc Process [_ {::process/keys [title end-time description] :as process}]
  {:query [::process/slug
           ::process/title
           ::process/description
           ::process/end-time
           {::process/moderators (comp/get-query Moderator)}]
   :ident ::process/slug
   :initial-state
   (fn [{:keys [slug]}]
     (when slug
       {::process/slug slug}))
   :use-hooks? true}
  (let [[description-open? set-description-open] (hooks/use-state false)]
    (layout/box {:mx 2 :my 1}
      (dd/typography {:component "h1" :variant "h2"} title)
      (when end-time
        (let [end-element (time/nice-time-element end-time)]
          (if (process/over? process)
            (process-ended-alert {:component end-element})
            (process-ends-alert {:component end-element}))))
      (description-collapse
        {:open? description-open?
         :toggle! set-description-open
         :description description}))))

(def ui-process (comp/factory Process))

(defsc ProcessHeader [_ {:keys [ui/current-process]}]
  {:query
   [{[:ui/current-process '_] (comp/get-query Process)}]
   :initial-state {}}
  (ui-process current-process))

(def ui-process-header (comp/factory ProcessHeader))

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
          (df/load! app process-ident Process
            {:target (targeting/multiple-targets
                       (targeting/replace-at (conj ident :process-header))
                       (targeting/replace-at [:ui/current-process])
                       (targeting/prepend-to [:all-processes])
                       (targeting/prepend-to [:root/all-processes]))
             :parallel true})
          (dr/target-ready! app ident))
        (do
          (mrg/merge-component! app ProcessContext (comp/get-initial-state ProcessContext {:slug slug}))
          (df/load! app process-ident Process
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
  {:query [{[:ui/current-process '_] (comp/get-query Process)}
           {:ui/process-header (comp/get-query ProcessHeader)}
           {:ui/process-router (comp/get-query ProcessRouter)}
           {:ui/new-proposal-dialog (comp/get-query new-proposal/NewProposalFormDialog)}
           {[:root/current-session '_] (comp/get-query auth/Session)}]
   :initial-state
   (fn [{:keys [slug]}]
     {:ui/process-router (comp/get-initial-state ProcessRouter)
      :ui/process-header (comp/get-initial-state ProcessHeader)
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
          (ui-process-header process-header)
          (tab-bar (current-target this)
            {:label (i18n/trc "Overview over process" "Overview") :target process.home/ProcessOverviewScreen}
            {:label (i18n/tr "All proposals") :target proposal.main-list/MainProposalList}
            {:label "Dashboard" :target process.dashboard/PersonalProcessDashboard}
            (when (process/moderator? current-process (:user current-session))
              {:label (i18n/trc "Link to moderation page" "Moderation") :target process.moderator/ProcessModeratorTab}))))
      (ui-process-router process-router
        {:show-new-proposal-dialog show-new-proposal-dialog})
      (new-proposal/ui-new-proposal-form new-proposal-dialog))))