(ns decide.ui.root
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.mutations :refer [defmutation]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [decide.routing :as r]
    [decide.ui.components.appbar :as appbar]
    [decide.ui.components.nav-drawer :as nav-drawer]
    [decide.ui.components.snackbar :as snackbar]
    [decide.ui.login :as login]
    [decide.ui.pages.settings :as settings]
    [decide.ui.pages.splash :as splash]
    [decide.ui.process.core :as process-page]
    [decide.ui.process.list :as process.list]
    [decide.ui.session :as session]
    [decide.ui.theming.dark-mode :as dark-mode]
    [decide.ui.theming.themes :as themes]
    [material-ui.data-display.list :as list]
    [material-ui.layout :as layout]
    [material-ui.styles :as styles]
    [material-ui.utils :as m.utils]
    [taoensso.timbre :as log]
    [decide.models.user :as user]))

(defrouter RootRouter [_this {:keys [current-state]}]
  {:router-targets [process.list/ProcessesPage
                    login/LoginPage
                    process-page/ProcessContext
                    login/SignUpPage
                    settings/SettingsPage]}

  (when-not current-state
    (dom/div {:dangerouslySetInnerHTML {:__html splash/splash}})))

(def ui-root-router (comp/factory RootRouter))

(defmutation set-theme [{:keys [theme]}]
  (action [{:keys [state]}]
    (swap! state assoc :root/theme theme)))

(defsc Root [this {:root/keys [theme root-router app-bar snackbar-container navdrawer]}]
  {:query [:root/theme
           {:root/app-bar (comp/get-query appbar/AppBar)}
           {:root/root-router (comp/get-query RootRouter)}
           {:root/snackbar-container (comp/get-query snackbar/SnackbarContainer)}
           {:root/navdrawer (comp/get-query nav-drawer/NavDrawer)}
           {:root/current-session (comp/get-query user/Session)}
           :all-processes]
   :initial-state
   (fn [_] {:root/root-router (comp/get-initial-state RootRouter)
            :root/theme (if (dark-mode/dark-mode?) :dark :light)
            :root/app-bar (comp/get-initial-state appbar/AppBar)
            :root/snackbar-container (comp/get-initial-state snackbar/SnackbarContainer)
            :root/navdrawer (comp/get-initial-state nav-drawer/NavDrawer)
            :root/current-session (comp/get-initial-state user/Session)
            :all-processes []})
   :use-hooks? true}
  (hooks/use-lifecycle
    (fn []
      (log/debug "Register dark-mode listener")
      (dark-mode/register-dark-mode-listener #(let [new-theme (if (.-matches %) :dark :light)]
                                                (comp/transact! (comp/any->app this)
                                                  [(set-theme {:theme new-theme})])))))
  (styles/theme-provider {:theme (themes/get-mui-theme theme)}
    (m.utils/css-baseline {})
    (appbar/ui-appbar app-bar {:menu-onClick nav-drawer/toggle-navdrawer!})
    (snackbar/ui-snackbar-container snackbar-container)

    (nav-drawer/ui-navdrawer navdrawer nil
      (list/list {}
        (list/item {:button true
                    :component :a
                    :href (r/path-to->absolute-url process.list/ProcessesPage)}
          (list/item-text {} "Processes"))
        (list/item {:button true
                    :component :a
                    :href (r/path-to->absolute-url settings/SettingsPage)}
          (list/item-text {} "Settings"))))

    (ui-root-router root-router)))
