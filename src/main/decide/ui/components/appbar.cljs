(ns decide.ui.components.appbar
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :refer [defmutation]]
    [material-ui.data-display :as dd]
    [material-ui.icons :as icons]
    [material-ui.inputs :as inputs]
    [material-ui.progress :as progress]
    [material-ui.surfaces :as surfaces]
    [material-ui.layout :as layout]
    [material-ui.transitions :as transitions]
    [com.fulcrologic.fulcro.mutations :as m]
    ["@material-ui/icons/AccountCircle" :default AccountCircleIcon]
    ["react" :as React]
    [material-ui.navigation :as navigation]))

(defmutation logout [params]
  (action [env] true))

(defsc AppBar
  [this
   {::app/keys [active-remotes]
    :keys      [ui/nav-open? ui/account-menu-open?]}]
  {:query          [[::app/active-remotes '_]
                    :ui/nav-open?
                    :ui/account-menu-open?]
   :ident          (fn [] [:component :app-bar])
   :initial-state  {:ui/nav-open?          false
                    :ui/account-menu-open? false}
   :initLocalState (fn initLocalState [this _] {:menu-ref (React/createRef)})}
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
          "decide")

        ; Spacer
        (layout/box {:flexGrow 1})

        (inputs/icon-button
          {:ref           (comp/get-state this :menu-ref)
           :edge          "end"
           :aria-label    "account of current user"
           :aria-controls "menuId"
           :aria-haspopup true
           :onClick       #(m/set-value! this :ui/account-menu-open? true)
           :color         "inherit"}
          (React/createElement AccountCircleIcon))
        (navigation/menu
          {:keepMounted        true
           :anchorEl           #(.-current (comp/get-state this :menu-ref))
           :getContentAnchorEl nil
           :anchorOrigin       {:vertical   "center"
                                :horizontal "left"}
           :transformOrigin    {:vertical   "top"
                                :horizontal "center"}
           :open               account-menu-open?
           :onClose            #(m/set-value! this :ui/account-menu-open? false)}
          (navigation/menu-item {} "Logout")))

      (layout/box {:height "4px"}
        (when loading?
          (progress/linear-progress {})))

      (transitions/collapse {:in nav-open?}
        (comp/children this)))))

(def ui-appbar (comp/computed-factory AppBar))