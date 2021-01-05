(ns decide.ui.root
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.mutations :refer [defmutation]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [com.fulcrologic.fulcro.routing.dynamic-routing :refer [defrouter]]
    [decide.ui.components.appbar :as appbar]
    [decide.ui.components.snackbar :as snackbar]
    [decide.ui.login :as login]
    [decide.ui.pages.splash :as splash]
    [decide.ui.process.core :as process-page]
    [decide.ui.proposal.detail-page :as detail-page]
    [decide.ui.proposal.main-proposal-list :as proposal-list]
    [decide.ui.theming.dark-mode :as dark-mode]
    [decide.ui.theming.themes :as themes]
    [material-ui.styles :as styles :refer [prefers-dark?]]
    [material-ui.utils :as mutils :refer [css-baseline]]
    [taoensso.timbre :as log]))

(defrouter RootRouter [_this {:keys [current-state]}]
  {:router-targets [login/LoginPage process-page/Process login/SignUpPage proposal-list/MainProposalList detail-page/ProposalPage]}
  (when-not current-state
    (dom/div {:dangerouslySetInnerHTML {:__html splash/splash}})))

(def ui-root-router (comp/factory RootRouter))

(defmutation set-theme [{:keys [theme]}]
  (action [{:keys [state]}]
    (swap! state assoc :ui/theme theme)))

(defsc Root [this {:ui/keys [theme root-router app-bar snackbar-container]}]
  {:query [:ui/theme
           {:ui/app-bar (comp/get-query appbar/AppBar)}
           {:ui/root-router (comp/get-query RootRouter)}
           {:ui/snackbar-container (comp/get-query snackbar/SnackbarContainer)}]
   :initial-state
   (fn [_] {:ui/root-router (comp/get-initial-state RootRouter)
            :ui/theme (if (dark-mode/dark-mode?) :dark :light)
            :ui/app-bar (comp/get-initial-state appbar/AppBar)
            :ui/snackbar-container (comp/get-initial-state snackbar/SnackbarContainer)})
   :use-hooks? true}
  (hooks/use-effect
    (fn []
      (log/debug "Register dark-mode listener")
      (dark-mode/register-dark-mode-listener #(let [new-theme (if (.-matches %) :dark :light)]
                                                (comp/transact! (comp/any->app this)
                                                  [(set-theme {:theme new-theme})]))))
    [])
  (styles/theme-provider {:theme (themes/get-mui-theme theme)}
    (mutils/css-baseline {})
    (appbar/ui-appbar app-bar {})
    (snackbar/ui-snackbar-container snackbar-container)
    (ui-root-router root-router)))
