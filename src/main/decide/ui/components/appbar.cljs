(ns decide.ui.components.appbar
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [decide.ui.session :as session]
    [material-ui.data-display :as dd]
    [material-ui.inputs :as inputs]
    [material-ui.layout :as layout]
    [material-ui.navigation :as navigation]
    [material-ui.surfaces :as surfaces]
    ["@material-ui/icons/AccountCircle" :default AccountCircleIcon]
    ["@material-ui/icons/Menu" :default Menu]
    [taoensso.timbre :as log]))

(def appbar-theme-color
  {:light "primary"
   :dark "inherit"})

(defsc AppBar
  [this
   {:keys [ui/account-menu-open? ui/theme]}
   {:keys [menu-onClick]}]
  {:query [:ui/account-menu-open?
           [:ui/theme '_]]
   :ident (fn [] [:component :app-bar])
   :initial-state {:ui/account-menu-open? false}
   :use-hooks? true}
  (let [logged-in? (session/logged-in? (hooks/use-context session/context))
        menu-ref (hooks/use-ref)
        [easteregg-count set-easteregg-count!] (hooks/use-state 0)
        show-easteregg? (and (zero? (mod easteregg-count 5)) (pos? easteregg-count))]
    (surfaces/app-bar
      {:position "sticky"
       :color (appbar-theme-color theme)
       :elevation 0}
      (surfaces/toolbar {}
        (when menu-onClick
          (inputs/icon-button
            {:edge :start
             :color :inherit
             :aria-label "menu"
             :onClick menu-onClick}
            (js/React.createElement Menu nil nil)))
        (dd/typography
          {:component :span
           :variant :h5
           :color "inherit"
           :onClick #(set-easteregg-count! (inc easteregg-count))}
          (if show-easteregg?
            "d-cider 🍾"
            "decide"))

        ; Spacer
        (layout/box {:display :flex
                     :flexGrow 1
                     :flexDirection :row-reverse}

          (if-not logged-in?
            (inputs/button
              {:variant :outlined
               :color :inherit
               :href "/login"}
              "Login")

            (inputs/icon-button
              {:ref menu-ref
               :edge "end"
               :aria-label "account of current user"
               :aria-controls "menuId"
               :aria-haspopup true
               :onClick #(m/set-value! this :ui/account-menu-open? true)
               :color "inherit"}
              (comp/create-element AccountCircleIcon nil nil)))

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
            (navigation/menu-item {} "Logout")))))))

(def ui-appbar (comp/computed-factory AppBar))