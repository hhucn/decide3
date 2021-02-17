(ns decide.ui.root
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.mutations :refer [defmutation]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [decide.models.authorization :as auth]
    [decide.ui.components.appbar :as appbar]
    [decide.ui.components.nav-drawer :as nav-drawer]
    [decide.ui.components.snackbar :as snackbar]
    [decide.ui.login :as login]
    [decide.ui.pages.settings :as settings]
    [decide.ui.pages.splash :as splash]
    [decide.ui.process.core :as process-page]
    [decide.ui.process.list :as process.list]
    [decide.ui.theming.dark-mode :as dark-mode]
    [decide.ui.theming.themes :as themes]
    [material-ui.inputs :as inputs]
    [material-ui.lab.alert :as alert]
    [material-ui.styles :as styles]
    [material-ui.utils :as m.utils]
    [taoensso.timbre :as log]))

(defrouter RootRouter [_this {:keys [current-state]}]
  {:router-targets [process.list/ProcessesPage
                    process-page/ProcessContext
                    settings/SettingsPage]}

  (when-not current-state
    (dom/div {:dangerouslySetInnerHTML {:__html splash/splash}})))

(def ui-root-router (comp/factory RootRouter))

(defmutation set-theme [{:keys [theme]}]
  (action [{:keys [state]}]
    (swap! state assoc :ui/theme theme)))

(defsc Root [this {:root/keys [root-router app-bar snackbar-container navdrawer]
                   :keys [ui/theme ui/login-dialog]}]
  {:query [:ui/theme
           {:ui/login-dialog (comp/get-query login/LoginDialog)}
           {:root/app-bar (comp/get-query appbar/AppBar)}
           {:root/root-router (comp/get-query RootRouter)}
           {:root/snackbar-container (comp/get-query snackbar/SnackbarContainer)}
           {:root/navdrawer (comp/get-query nav-drawer/NavDrawer)}
           {:root/current-session (comp/get-query auth/Session)}
           :all-processes
           {::i18n/current-locale (comp/get-query i18n/Locale)}]
   :initial-state
   (fn [_] {:root/root-router (comp/get-initial-state RootRouter)
            :ui/login-dialog (comp/get-initial-state login/LoginDialog)
            :ui/theme (if (dark-mode/dark-mode?) :dark :light)
            :root/app-bar (comp/get-initial-state appbar/AppBar)
            :root/snackbar-container (comp/get-initial-state snackbar/SnackbarContainer)
            :root/navdrawer (comp/get-initial-state nav-drawer/NavDrawer)
            :root/current-session (comp/get-initial-state auth/Session)
            :all-processes []
            ::i18n/current-locale nil})
   :use-hooks? true}
  (hooks/use-lifecycle
    (fn []
      (log/debug "Register dark-mode listener")
      (dark-mode/register-dark-mode-listener #(let [new-theme (if (.-matches %) :dark :light)]
                                                (comp/transact! (comp/any->app this)
                                                  [(set-theme {:theme new-theme})])))))
  (let [[show-wip-warning? set-wip-warning] (hooks/use-state true)]
    (styles/theme-provider {:theme (themes/get-mui-theme theme)}
      (m.utils/css-baseline {})
      (appbar/ui-appbar app-bar {:menu-onClick nav-drawer/toggle-navdrawer!})
      (snackbar/ui-snackbar-container snackbar-container)

      (nav-drawer/ui-navdrawer navdrawer)
      (login/ui-login-modal login-dialog)

      (when show-wip-warning?
        (alert/alert
          {:severity :warning
           :action (inputs/button {:onClick #(set-wip-warning false)} (i18n/trc "Dismiss warning alert" "Dismiss"))}
          (dom/strong {} (i18n/tr "Work in progress!")) " " (i18n/tr "Components marked with a yellow outline are in progress, and may not work yet!")))
      (ui-root-router root-router))))
