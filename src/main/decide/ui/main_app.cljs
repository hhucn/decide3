(ns decide.ui.main-app
  (:require
    [material-ui.layout :as layout]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [decide.ui.components.appbar :as appbar]
    [material-ui.surfaces :as surfaces]
    [material-ui.utils :as mutils :refer [css-baseline]]
    [material-ui.data-display :as dd]
    [decide.ui.proposal.page :as proposal-page]
    [decide.ui.proposal.card :as proposal-card]
    [decide.ui.proposal.main-proposal-list :as main-proposal-list]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [decide.ui.pages.settings :as settings]
    [decide.routing :as routing]))

(defrouter ContentRouter [this props]
  {:router-targets [main-proposal-list/MainProposalList settings/SettingsPage proposal-page/ProposalPage]})

(def ui-content-router (comp/factory ContentRouter))

(defn list-item-link [props & children]
  (apply
    dd/list-item
    (merge
      {:button    true
       :component "a"}
      props)
    children))

(defn nav-entry [component {:keys [label path]}]
  (list-item-link
    {:href     (routing/path->url path)
     :selected (= (dr/current-route component) path)}
    (dd/list-item-text
      {:primary label})))

(defsc MainApp [this {:keys [ui/content-router ui/app-bar]}]
  {:query         [{:ui/content-router (comp/get-query ContentRouter)}
                   {:ui/app-bar (comp/get-query appbar/AppBar)}]
   :ident         (fn [] [:page/id :main-app])
   :initial-state (fn [_] {:ui/content-router (comp/get-initial-state ContentRouter)
                           :ui/app-bar        (comp/get-initial-state appbar/AppBar)})
   :route-segment ["app"]
   :will-enter    (fn will-enter [app _]
                    (dr/route-deferred (comp/get-ident MainApp nil)
                      #(df/load! app :all-proposals proposal-card/Proposal
                         {:post-mutation        `dr/target-ready
                          :post-mutation-params {:target (comp/get-ident MainApp nil)}})))}
  (mutils/css-baseline {}
    (appbar/ui-appbar app-bar {}
      (dd/list {}
        (nav-entry this
          {:label "Vorschläge"
           :path  (dr/path-to MainApp main-proposal-list/MainProposalList)})
        (nav-entry this
          {:label "Einstellungen"
           :path  (dr/path-to main-proposal-list/MainProposalList settings/SettingsPage)})))
    (layout/box {:flex 1 :clone true}
      (surfaces/paper {:component :main}
        (ui-content-router content-router)))))