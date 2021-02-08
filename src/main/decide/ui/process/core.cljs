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
    [decide.routing :as r]
    [decide.ui.common.time :as time]
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

(defn current-target [c]
  (dr/route-target ProcessRouter (dr/current-route c ProcessRouter)))

(def ui-process-router (comp/computed-factory ProcessRouter))

(defsc ProcessHeader [_ {::process/keys [title end-time]}]
  {:query [::process/slug ::process/title ::process/end-time]
   :ident ::process/slug}
  (layout/box {:mx 2 :mb 2 :mt 0}
    (dd/typography {:component "h1" :variant "h2"} title)
    (when end-time
      (let [over? (time/in-past? end-time)
            end-element (time/time-element end-time
                          (time/nice-string end-time {:dateprefix " am "}))]
        (dd/typography {:variant "subtitle1" :color (when over? "error")}
          (if over?
            ["Endete" end-element "!"]
            ["Endet" end-element "."]))))))

(def ui-process-info (comp/factory ProcessHeader))

(defn tab-bar [{current-target :target} & targets]
  (let [lookup-map (zipmap (map :target targets) (range))
        current-index (get lookup-map current-target false)]
    (tabs/tabs {:value current-index
                :indicatorColor "secondary"
                :textColor "secondary"}
      (for [{:keys [label target]} targets]
        (tabs/tab {:label label
                   :key label
                   :href (r/path->url (dr/subpath target))})))))

(defsc Process [_ _]
  {:query [::process/slug ::process/end-time]
   :ident ::process/slug
   :initial-state
   (fn [{:keys [slug]}]
     (when slug
       {::process/slug slug}))})

(defsc ProcessContext [this {:ui/keys [process-router new-proposal-dialog]
                             :keys [process-header ::process/slug process]}]
  {:query [::process/slug
           {:process (comp/get-query Process)}
           {:process-header (comp/get-query ProcessHeader)}
           {:ui/process-router (comp/get-query ProcessRouter)}
           {:ui/new-proposal-dialog (comp/get-query new-proposal/NewProposalFormDialog)}]
   :initial-state
   (fn [{:keys [slug]}]
     {::process/slug slug
      :process (comp/get-initial-state Process {:slug slug})
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
  (let [show-new-proposal-dialog (hooks/use-callback #(comp/transact! this [(new-proposal/show {:slug slug})]))
        {::process/keys [end-time]} process
        process-over? (and (some? end-time) (time/in-past? end-time))]
    (comp/fragment
      (surfaces/paper
        {:square true}
        (layout/container {:maxWidth :xl :disableGutters true}
          (when process-header
            (ui-process-info process-header))
          (tab-bar (current-target this)
            {:label "Übersicht" :target process.home/ProcessHome}
            {:label "Alle Vorschläge" :target proposal.main-list/MainProposalList})))
      (ui-process-router process-router
        {:slug slug
         :process process
         :process-over? process-over?
         :show-new-proposal-dialog show-new-proposal-dialog})
      (new-proposal/ui-new-proposal-form new-proposal-dialog))))