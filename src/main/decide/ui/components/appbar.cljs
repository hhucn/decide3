(ns decide.ui.components.appbar
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [material-ui.data-display :as dd]
    [material-ui.icons :as icons]
    [material-ui.inputs :as inputs]
    [material-ui.progress :as progress]
    [material-ui.surfaces :as surfaces]
    [material-ui.layout :as layout]
    [material-ui.transitions :as transitions]
    [com.fulcrologic.fulcro.mutations :as m]))

(defsc AppBar
  [this
   {::app/keys [active-remotes]
    :keys      [ui/nav-open?]}]
  {:query         [[::app/active-remotes '_]
                   :ui/nav-open?]
   :ident (fn [] [:component :app-bar])
   :initial-state {:ui/nav-open? false}
   :use-hooks?    true}
  (let [loading? (#{:remote} active-remotes)]
    (surfaces/app-bar
      {:position  "sticky"
       :elevation 0}
      (surfaces/toolbar {}
        (inputs/icon-button
          {:edge       :start
           :color      :inherit
           :aria-label "menu"
           :onClick    #(m/toggle! this :ui/nav-open?)}
          (icons/menu {}))
        (dd/typography
          {:component :h1
           :variant   :h4
           :color     "inherit"}
          "decide"))

      (layout/box {:height "4px"}
        (when loading?
          (progress/linear-progress {})))

      (transitions/collapse {:in nav-open?}
        (comp/children this)))))

(def ui-appbar (comp/computed-factory AppBar))