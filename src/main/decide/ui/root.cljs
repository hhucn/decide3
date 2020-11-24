(ns decide.ui.root
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]

    [decide.models.proposal :as proposal]
    [decide.ui.components.NewProposal :as new-proposal]
    [decide.ui.login :as login]
    [decide.ui.main-app :as todo-app]
    [decide.ui.pages.splash :as splash]
    [decide.ui.theming.themes :as themes]
    [decide.ui.theming.dark-mode :as dark-mode]

    [material-ui.utils :as mutils :refer [css-baseline]]
    [material-ui.styles :as styles :refer [prefers-dark?]]

    [taoensso.timbre :as log]))

(defrouter PageRouter [_this {:keys [current-state]}]
  {:router-targets [login/LoginPage todo-app/MainApp login/SignUpPage]}
  (when-not current-state splash/splash))

(def ui-page-router (comp/factory PageRouter))

(defmutation set-theme [{:keys [theme]}]
  (action [{:keys [state]}]
    (swap! state assoc :ui/theme theme)))

(defsc Root [this {:keys [ui/theme ui/page-router new-proposal-dialog]}]
  {:query      [:ui/theme
                {:ui/page-router (comp/get-query PageRouter)}
                {:all-proposals (comp/get-query proposal/Proposal)}
                {:new-proposal-dialog (comp/get-query new-proposal/NewProposalFormDialog)}]
   :initial-state
               (fn [_] {:ui/page-router      (comp/get-initial-state PageRouter)
                        :ui/theme            (if (dark-mode/dark-mode?) :dark :light)
                        :all-proposals       []
                        :new-proposal-dialog (comp/get-initial-state new-proposal/NewProposalFormDialog)})
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
    (ui-page-router page-router)
    (new-proposal/ui-new-proposal-form2 new-proposal-dialog)))
