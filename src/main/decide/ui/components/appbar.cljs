(ns decide.ui.components.appbar
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [material-ui.data-display :as dd]
    [material-ui.icons :as icons]
    [material-ui.inputs :as inputs]
    [material-ui.progress :as progress]
    [material-ui.surfaces :as surfaces]))

(defsc AppBar
  [_this
   {::app/keys [active-remotes]}
   {:keys [on-menu-click]}]
  {:query         [[::app/active-remotes '_]]
   :initial-state {}
   :use-hooks?    true}
  (let [loading? (#{:remote} active-remotes)]
    (surfaces/app-bar
      {:position "static"
       :color "transparent"
       :elevation 0}
      (surfaces/toolbar {}
        (when on-menu-click
          (inputs/icon-button
            {:edge       :start
             :color      :inherit
             :aria-label "menu"
             :onClick    on-menu-click}
            (icons/menu {})))
        (dd/typography
          {:component :h1
           :variant :h4
           :color "inherit"}
          "decide"))

      (dom/div {:style {:height "4px"}}
        (when loading?
          (progress/linear-progress {}))))))

(def ui-appbar (comp/computed-factory AppBar))