(ns decide.ui.root
  (:require
   [com.fulcrologic.fulcro-i18n.i18n :as i18n]
   [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
   [com.fulcrologic.fulcro.dom :as dom]
   [com.fulcrologic.fulcro.mutations :refer [defmutation]]
   [com.fulcrologic.fulcro.react.hooks :as hooks]
   [com.fulcrologic.fulcro.routing.dynamic-routing :refer [defrouter]]
   [decide.models.authorization :as auth]
   [decide.ui.components.appbar :as appbar]
   [decide.ui.components.nav-drawer :as nav-drawer]
   [decide.ui.components.snackbar :as snackbar]
   [decide.ui.login :as login]
   [decide.ui.meta :as meta]
   [decide.ui.pages.help :as help]
   [decide.ui.pages.settings :as settings]
   [decide.ui.pages.splash :as splash]
   [decide.ui.process.core :as process-page]
   [decide.ui.process.list :as process.list]
   [decide.ui.storage :as storage]
   [decide.ui.theming.dark-mode :as dark-mode]
   [decide.ui.theming.themes :as themes]
   [mui.styles :as styles]
   [mui.utils :as m.utils]
   [mui.x.date-pickers :as date-pickers]
   [taoensso.timbre :as log]
   ["@mui/x-date-pickers/AdapterDateFns" :refer [AdapterDateFns]]))

(defrouter RootRouter [_this {:keys [current-state]}]
  {:router-targets [process.list/ProcessesPage
                    process-page/ProcessContext
                    settings/SettingsPage
                    help/InstructionPage]}

  (when-not current-state
    (dom/div {:dangerouslySetInnerHTML {:__html splash/splash}})))

(def ui-root-router (comp/factory RootRouter))

(defmutation set-theme [{:keys [theme]}]
  (action [{:keys [state]}]
    (swap! state assoc :ui/theme theme)))

(defsc Root [this {:root/keys [root-router app-bar snackbar-container navdrawer]
                   :keys [ui/theme ui/login-dialog]
                   :as props}]
  {:query [:ui/theme
           {:ui/login-dialog (comp/get-query login/LoginDialog)}
           {:root/app-bar (comp/get-query appbar/AppBar)}
           {:root/root-router (comp/get-query RootRouter)}
           {:root/snackbar-container (comp/get-query snackbar/SnackbarContainer)}
           {:root/navdrawer (comp/get-query nav-drawer/NavDrawer)}
           {:root/current-session (comp/get-query auth/Session)}
           :ui/current-process
           :root/all-processes
           {::i18n/current-locale (comp/get-query i18n/Locale)}
           {meta/root-key (comp/get-query meta/Meta)}
           {storage/localstorage-key (comp/get-query storage/LocalStorage)}]
   :initial-state
   (fn [_] {:root/root-router (comp/get-initial-state RootRouter)
            :ui/login-dialog (comp/get-initial-state login/LoginDialog)
            :ui/theme (if (dark-mode/dark-mode?) :dark :light)
            :root/app-bar (comp/get-initial-state appbar/AppBar)
            :root/snackbar-container (comp/get-initial-state snackbar/SnackbarContainer)
            :root/navdrawer (comp/get-initial-state nav-drawer/NavDrawer)
            :root/current-session (comp/get-initial-state auth/Session)
            :root/all-processes []
            ::i18n/current-locale nil
            storage/localstorage-key (comp/get-initial-state storage/LocalStorage)
            meta/root-key (comp/get-initial-state meta/Meta {:title "decide"})})
   :use-hooks? true}
  (let [localstorage (storage/localstorage-key props)
        manual-theme (keyword (:theme localstorage))]
    (hooks/use-lifecycle
      (fn []
        (comp/transact! this [(meta/set-meta {:lang (name (get-in props [::i18n/current-locale ::i18n/locale]))})])
        (log/debug "Register dark-mode listener")
        (dark-mode/register-dark-mode-listener
          #(let [new-theme (if (.-matches %) :dark :light)]
             (comp/transact! (comp/any->app this)
               [(set-theme {:theme new-theme})])))))
    (date-pickers/localization-provider #js {:dateAdapter AdapterDateFns}
      (styles/theme-provider {:theme (themes/get-mui-theme {:mode (if (= :auto manual-theme) theme manual-theme)
                                                            :source-color "#0061A7"})}
        (meta/ui-meta (get props meta/root-key))
        (storage/ui-localstorage localstorage)
        (m.utils/css-baseline {})
        (appbar/ui-appbar app-bar {:menu-onClick nav-drawer/toggle-navdrawer!})
        (snackbar/ui-snackbar-container snackbar-container)

        (nav-drawer/ui-navdrawer navdrawer)
        (login/ui-login-modal login-dialog)

        (ui-root-router root-router)))))
