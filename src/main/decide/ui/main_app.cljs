(ns decide.ui.main-app
  (:require [decide.ui.sidedrawer :as sidedrawer]
            [material-ui.layout :as layout]
            [com.fulcrologic.fulcro.application :as app]
            [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
            [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom :refer [div]]
            [decide.ui.components.NewProposal :as new-proposal]
            [decide.ui.components.appbar :as appbar]
            [material-ui.surfaces :as surfaces]
            [material-ui.inputs :as inputs]
            [material-ui.icons :as icons]
            [material-ui.progress :as progress]
            [material-ui.utils :as mutils :refer [css-baseline]]
            [material-ui.data-display :as dd]
            [decide.models.proposal :as proposal]
            [taoensso.timbre :as log]
            [decide.ui.themes :as themes]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [decide.ui.settings :as settings]
            ["@material-ui/icons/Add" :default Add]
            ["react" :as React]
            [com.fulcrologic.fulcro.mutations :as m]
            [decide.ui.components.breadcrumbs :as breadcrumbs]
            [decide.routing :as routing]
            [clojure.string :as str]
            [decide.utils :as utils]))

(defn add-proposal-fab [props]
  (inputs/fab
    (merge
      {:aria-label "Neuer Vorschlag"
       :title      "Neuer Vorschlag"
       :color      "secondary"
       :variant    (if (utils/>=-breakpoint? "sm") "extended" "round")
       :style      {:position "fixed"
                    :bottom   "16px"
                    :right    "16px"}}
      props)
    (React/createElement Add)
    (when (utils/>=-breakpoint? "sm")
      "Neuer Vorschlag")))


(defn empty-proposal-list-message []
  (layout/box {:p 2 :mx "auto"}
    (dd/typography {:align "center"
                    :color "textSecondary"}
      "Bisher gibt es keine Vorschläge.")))

(defsc MainProposalList [this {:keys [all-proposals new-proposal-modal-open? new-proposal-form]}]
  {:query         [{[:all-proposals '_] (comp/get-query proposal/Proposal)}
                   :new-proposal-modal-open?
                   {:new-proposal-form (comp/get-query new-proposal/NewProposalForm)}]
   :ident         (fn [] [:content/id :main-proposal-list])
   :initial-state (fn [_]
                    {:new-proposal-modal-open? false
                     :new-proposal-form (comp/get-initial-state new-proposal/NewProposalForm)})
   :route-segment ["home"]
   :use-hooks? true}
  (layout/container {:maxWidth :md}
    (breadcrumbs/breadcrumb-nav
      [["Vorschläge" ""]])
    (if (empty? all-proposals)
      (empty-proposal-list-message)
      (dd/list {:disablePadding true}
        (for [proposal all-proposals]
          (dd/list-item {:disableGutters true :key (:proposal/id proposal)}
            (proposal/ui-proposal proposal)))))

    (add-proposal-fab {:onClick #(m/set-value! this :new-proposal-modal-open? true)})
    (new-proposal/new-proposal-dialog
      {:open? new-proposal-modal-open?
       :onClose #(m/set-value! this :new-proposal-modal-open? false)} ; TODO Reset form on close
      (new-proposal/ui-new-proposal-form new-proposal-form {:onClose #(m/set-value! this :new-proposal-modal-open? false)}))))

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

(defsc MainApp [this
                {:keys [ui/nav-drawer ui/content-router ui/app-bar]}
                _computed
                {:keys [nav-collapsed animation]}]
  {:query         [{:ui/content-router (comp/get-query ContentRouter)}
                   {:ui/app-bar (comp/get-query appbar/AppBar)}
                   {:ui/nav-drawer (comp/get-query sidedrawer/NavDrawer)}]
   :ident         (fn [] [:page/id :main-app])
   :initial-state (fn [_] {:ui/content-router (comp/get-initial-state ContentRouter)
                           :ui/nav-drawer     (comp/get-initial-state sidedrawer/NavDrawer) :ui/app-bar (comp/get-initial-state appbar/AppBar)})
   :route-segment ["app"]
   :will-enter    (fn will-enter [app _]
                    (dr/route-deferred (comp/get-ident MainApp nil)
                      #(df/load! app :all-proposals proposal/Proposal
                         {:post-mutation        `dr/target-ready
                          :post-mutation-params {:target (comp/get-ident MainApp nil)}})))
   :css           [[:.animation {:max-height "110px"
                                 :transition ((get-in themes/shared [:transitions :create])
                                              #js ["max-height"]
                                              #js {:easing   (get-in themes/shared [:transitions :easing :sharp])
                                                   :duration (get-in themes/shared [:transitions :duration :leavingScreen])})}]
                   [:.nav-collapsed {:max-height "0"
                                     :overflow   "hidden"
                                     :transition ((get-in themes/shared [:transitions :create])
                                                  #js ["max-height"]
                                                  #js {:easing   (get-in themes/shared [:transitions :easing :easeOut])
                                                       :duration (get-in themes/shared [:transitions :duration :enteringScreen])})}]]}
  (let [collapsed? (not (:ui/open? nav-drawer))]
    (comp/fragment
      (mutils/css-baseline {})
      (dom/div {:style {:color    "white"
                        :position "sticky"}}
        (appbar/ui-appbar app-bar
          {:on-menu-click #(sidedrawer/toggle-drawer! this)})
        (dd/list {:className      (str/join " " [animation (when collapsed? nav-collapsed)])
                  :disablePadding true
                  :component      "nav"}
          (nav-entry this
            {:label "Vorschläge"
             :path  (dr/path-to
                      (comp/registry-key->class 'decide.ui.main-app/MainApp)
                      (comp/registry-key->class 'decide.ui.main-app/MainProposalList))})
          #_(dd/divider {})
          (nav-entry this
            {:label "Einstellungen"
             :path  (dr/path-to
                      (comp/registry-key->class 'decide.ui.main-app/MainApp)
                      settings/SettingsPage)})))
      (surfaces/paper {:component :main
                       :style     {:flex 1}}
        (dom/main
          (ui-content-router content-router))))))