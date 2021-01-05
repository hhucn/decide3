(ns decide.ui.components.appbar
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [material-ui.data-display :as dd]
    [material-ui.icons :as icons]
    [material-ui.inputs :as inputs]
    [material-ui.layout :as layout]
    [material-ui.navigation :as navigation]
    [material-ui.progress :as progress]
    [material-ui.surfaces :as surfaces]
    [material-ui.transitions :as transitions]
    ["@material-ui/icons/AccountCircle" :default AccountCircleIcon]))

(def appbar-theme-color
  {:light "primary"
   :dark "inherit"})

(defsc AppBar
  [this
   {::app/keys [active-remotes]
    :keys [ui/nav-open? ui/account-menu-open? ui/theme]}]
  {:query [[::app/active-remotes '_]
           :ui/nav-open?
           :ui/account-menu-open?
           [:ui/theme '_]]
   :ident (fn [] [:component :app-bar])
   :initial-state {:ui/nav-open? false
                   :ui/account-menu-open? false}
   :use-hooks? true}
  (let [menu-ref (hooks/use-ref)
        loading? (#{:remote} active-remotes)]
    (surfaces/app-bar
      {:position "sticky"
       :color (appbar-theme-color theme)
       :elevation 0}
      (surfaces/toolbar {}
        #_(inputs/icon-button
            {:edge :start
             :color :inherit
             :aria-label "menu"
             :onClick #(m/toggle! this :ui/nav-open?)}
            (icons/menu {}))
        (dd/typography
          {:component :span
           :variant :h5
           :color "inherit"}
          "decide")

        ; Spacer
        (layout/box {:flexGrow 1})

        (inputs/icon-button
          {:ref menu-ref
           :edge "end"
           :aria-label "account of current user"
           :aria-controls "menuId"
           :aria-haspopup true
           :onClick #(m/set-value! this :ui/account-menu-open? true)
           :color "inherit"}
          (comp/create-element AccountCircleIcon nil nil))
        (navigation/menu
          {:keepMounted true
           :anchorEl (.-current menu-ref)
           :getContentAnchorEl nil
           :anchorOrigin {:vertical "bottom"
                          :horizontal "left"}
           :transformOrigin {:vertical "top"
                             :horizontal "center"}
           :open account-menu-open?
           :onClose #(m/set-value! this :ui/account-menu-open? false)}
          (navigation/menu-item {} "Logout")))

      (layout/box {:height "4px"}
        (when loading?
          (progress/linear-progress {})))

      (transitions/collapse {:in nav-open?}
        (comp/children this)))))

(def ui-appbar (comp/computed-factory AppBar))