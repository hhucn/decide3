(ns decide.ui.main-app
  (:require [decide.ui.sidedrawer :as sidedrawer]
            [material-ui.layout :as layout]
            [com.fulcrologic.fulcro.application :as app]
            [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
            [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom :refer [div]]
            [decide.ui.components.NewProposal :as new-proposal]
            [material-ui.surfaces :as surfaces]
            [material-ui.inputs :as inputs]
            [material-ui.icons :as icons]
            [material-ui.progress :as progress]
            [material-ui.utils :as mutils :refer [css-baseline]]
            [material-ui.data-display :as dd]
            [decide.models.proposal :as proposal]
            [material-ui.styles :as styles]
            [taoensso.timbre :as log]
            [decide.ui.themes :as themes]
            [com.fulcrologic.fulcro-css.css :as css]
            [clojure.string :as str]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [decide.ui.settings :as settings]
            [material-ui.inputs :as input]
            ["@material-ui/icons/Add" :default Add]
            ["react" :as React]
            [material-ui.feedback :as feedback]
            [com.fulcrologic.fulcro.mutations :as m]
            [material-ui.navigation :as navigation]
            [decide.ui.components.breadcrumbs :as breadcrumbs]))

(defn add-proposal-fab [props]
  (input/fab
    (merge
      {:aria-label "Neuer Vorschlag"
       :title "Neuer Vorschlag"
       :color "secondary"
       :style {:position "fixed"
               :bottom "24px"
               :right "24px"}}
      props)
   (React/createElement Add)))

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
  (layout/container {:maxWidth :lg}
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


(defsc AppBar [this
               {::app/keys [active-remotes]}
               {:keys [on-menu-click]}
               {:keys [appbar]}]
  {:query         [[::app/active-remotes '_]]
   :css           [[:.appbar {:z-index       (dec (get-in themes/shared [:zIndex :modal]))}]]
   :initial-state {}}
  (let [loading? (not (empty? active-remotes))]
    (surfaces/app-bar
      {:position  :sticky
       :className appbar}
      (surfaces/toolbar
        {}
        (when on-menu-click
          (inputs/icon-button
            {:edge       :start
             :color      :inherit
             :aria-label :menu
             :onClick    on-menu-click}
            (icons/menu {})))
        (dd/typography
          {:component :h1
           :variant :h4}
          "decide"))

      (dom/div {:style {:height "4px"}}
        (when loading?
          (progress/linear-progress))))))

(def ui-appbar (comp/computed-factory AppBar))

(defsc MainApp [this
                {:keys [ui/nav-drawer ui/content-router ui/app-bar]}
                _computed
                {:keys [with-appbar appbar-shifted]}]
  {:query         [{:ui/content-router (comp/get-query ContentRouter)}
                   {:ui/app-bar (comp/get-query AppBar)}
                   {:ui/nav-drawer (comp/get-query sidedrawer/NavDrawer)}]
   :ident         (fn [] [:page/id :main-app])
   :initial-state (fn [_] {:ui/content-router (comp/get-initial-state ContentRouter)
                           :ui/nav-drawer     (comp/get-initial-state sidedrawer/NavDrawer)
                           :ui/app-bar        (comp/get-initial-state AppBar)})
   :route-segment ["app"]
   :will-enter    (fn will-enter [app _]
                    (dr/route-deferred (comp/get-ident MainApp nil)
                      #(df/load! app :all-proposals proposal/Proposal
                         {:post-mutation        `dr/target-ready
                          :post-mutation-params {:target (comp/get-ident MainApp nil)}})))
   :css           [[:.with-appbar {:color      :black
                                   :transition ((get-in themes/shared [:transitions :create])
                                                #js ["margin" "width"]
                                                #js {:easing   (get-in themes/shared [:transitions :easing :sharp])
                                                     :duration (get-in themes/shared [:transitions :duration :leavingScreen])})}]
                   [:.appbar-shifted {:margin-left "240px"
                                      :transition  ((get-in themes/shared [:transitions :create])
                                                    #js ["margin" "width"]
                                                    #js {:easing   (get-in themes/shared [:transitions :easing :easeOut])
                                                         :duration (get-in themes/shared [:transitions :duration :enteringScreen])})}]]}
  (let [shift? (:ui/open? nav-drawer)]
    (div
      (mutils/css-baseline {})
      (ui-appbar app-bar
        #_{:on-menu-click #(sidedrawer/toggle-drawer! this)})
      (dom/main {:classes [with-appbar (when shift? appbar-shifted)]}
        (ui-content-router content-router))
      (sidedrawer/ui-nav-drawer nav-drawer))))