(ns decide.ui.main-app
  (:require
    [material-ui.layout :as layout]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [decide.ui.components.NewProposal :as new-proposal]
    [decide.ui.components.appbar :as appbar]
    [material-ui.surfaces :as surfaces]
    [material-ui.inputs :as inputs]
    [material-ui.utils :as mutils :refer [css-baseline]]
    [material-ui.data-display :as dd]
    [decide.models.proposal :as proposal]
    [taoensso.timbre :as log]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [decide.ui.pages.settings :as settings]
    ["@material-ui/icons/Add" :default Add]
    ["react" :as React]
    [com.fulcrologic.fulcro.mutations :as m]
    [decide.ui.components.breadcrumbs :as breadcrumbs]
    [decide.routing :as routing]
    [decide.utils :as utils]
    [com.fulcrologic.fulcro.react.hooks :as hooks]))

(defn add-proposal-fab [props]
  (layout/box
    {:position "fixed"
     :bottom   "16px"
     :right    "16px"}
    (inputs/fab
      (merge
        {:aria-label "Neuer Vorschlag"
         :title      "Neuer Vorschlag"
         :color      "secondary"
         :variant    (if (utils/>=-breakpoint? "sm") "extended" "round")}
        props)
      (React/createElement Add)
      (when (utils/>=-breakpoint? "sm")
        "Neuer Vorschlag"))))


(defn empty-proposal-list-message []
  (layout/box {:p 2 :mx "auto"}
    (dd/typography {:align "center"
                    :color "textSecondary"}
      "Bisher gibt es keine Vorschläge.")))

(defsc MainProposalList [this {:keys [all-proposals]}]
  {:query         [{[:all-proposals '_] (comp/get-query proposal/Proposal)}]
   :ident         (fn [] [:content/id :main-proposal-list])
   :initial-state {:all-proposals []}
   :route-segment ["home"]
   :use-hooks?    true}
  (let [open-new-proposal-dialog (hooks/use-callback #(comp/transact! this [(new-proposal/show-new-proposal-form-dialog {})]))]
    (comp/fragment
      (layout/container {:maxWidth :md}
        (breadcrumbs/breadcrumb-nav
          [["Vorschläge" ""]])
        (if (empty? all-proposals)
          (empty-proposal-list-message)
          (layout/box {:pb 8 :clone true}                   ; to not cover up the FAB
            (dd/list {}
              (for [proposal all-proposals]
                (dd/list-item {:disableGutters true :key (:proposal/id proposal)}
                  (proposal/ui-proposal proposal)))))))

      (add-proposal-fab {:onClick open-new-proposal-dialog}))))

(defrouter ContentRouter [this props]
  {:router-targets [MainProposalList settings/SettingsPage proposal/ProposalPage]})

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
                      #(df/load! app :all-proposals proposal/Proposal
                         {:post-mutation        `dr/target-ready
                          :post-mutation-params {:target (comp/get-ident MainApp nil)}})))}
  (mutils/css-baseline {}
    (appbar/ui-appbar app-bar {}
      (dd/list {}
        (nav-entry this
          {:label "Vorschläge"
           :path  (dr/path-to MainApp MainProposalList)})
        (nav-entry this
          {:label "Einstellungen"
           :path  (dr/path-to MainProposalList settings/SettingsPage)})))
    (layout/box {:flex 1 :clone true}
      (surfaces/paper {:component :main}
        (ui-content-router content-router)))))