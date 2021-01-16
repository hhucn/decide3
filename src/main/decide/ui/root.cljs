(ns decide.ui.root
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.mutations :refer [defmutation]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [com.fulcrologic.fulcro.routing.dynamic-routing :refer [defrouter]]
    [decide.ui.components.appbar :as appbar]
    [decide.ui.components.nav-drawer :as nav-drawer]
    [decide.ui.components.snackbar :as snackbar]
    [decide.ui.login :as login]
    [decide.ui.pages.splash :as splash]
    [decide.ui.process.core :as process-page]
    [decide.ui.session :as session]
    [decide.ui.theming.dark-mode :as dark-mode]
    [decide.ui.theming.themes :as themes]
    [material-ui.layout :as layout]
    [material-ui.styles :as styles :refer [prefers-dark?]]
    [material-ui.utils :as mutils :refer [css-baseline]]
    [taoensso.timbre :as log]))

(defrouter RootRouter [_this {:keys [current-state]}]
  {:router-targets [login/LoginPage process-page/ProcessContext login/SignUpPage]}
  (when-not current-state
    (dom/div {:dangerouslySetInnerHTML {:__html splash/splash}})))

(def ui-root-router (comp/factory RootRouter))

(defmutation set-theme [{:keys [theme]}]
  (action [{:keys [state]}]
    (swap! state assoc :ui/theme theme)))

(defsc Root [this {:ui/keys [theme root-router app-bar snackbar-container navdrawer]
                   :keys [AUTH]}]
  {:query [:ui/theme
           :AUTH
           {:ui/app-bar (comp/get-query appbar/AppBar)}
           {:ui/root-router (comp/get-query RootRouter)}
           {:ui/snackbar-container (comp/get-query snackbar/SnackbarContainer)}
           {:ui/navdrawer (comp/get-query nav-drawer/NavDrawer)}]
   :initial-state
   (fn [_] {:ui/root-router (comp/get-initial-state RootRouter)
            :ui/theme (if (dark-mode/dark-mode?) :dark :light)
            :ui/app-bar (comp/get-initial-state appbar/AppBar)
            :ui/snackbar-container (comp/get-initial-state snackbar/SnackbarContainer)
            :ui/navdrawer (comp/get-initial-state nav-drawer/NavDrawer)})
   :use-hooks? true}
  (hooks/use-lifecycle
    (fn []
      (log/debug "Register dark-mode listener")
      (dark-mode/register-dark-mode-listener #(let [new-theme (if (.-matches %) :dark :light)]
                                                (comp/transact! (comp/any->app this)
                                                  [(set-theme {:theme new-theme})])))))
  (js/React.createElement (.-Provider session/context) #js {:value (:current-session AUTH nil)}
    (styles/theme-provider {:theme (themes/get-mui-theme theme)}
      (mutils/css-baseline {})
      (layout/box {:mb 2}
        (appbar/ui-appbar app-bar #_{:menu-onClick nav-drawer/toggle-navdrawer!}))
      (snackbar/ui-snackbar-container snackbar-container)
      (nav-drawer/ui-navdrawer navdrawer)
      (ui-root-router root-router))))
