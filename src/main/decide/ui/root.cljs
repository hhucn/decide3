(ns decide.ui.root
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [material-ui.utils :as mutils :refer [css-baseline]]
    [material-ui.styles :as styles :refer [prefers-dark?]]
    [decide.ui.themes :as themes]
    [taoensso.timbre :as log]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [decide.ui.main-app :as todo-app]
    [decide.ui.login :as login]
    [com.fulcrologic.fulcro-css.css-injection :as inj]
    [clojure.string :as str]
    [decide.models.proposal :as proposal]))


(def dark-mode-matcher
  (and
    (-> js/window .-matchMedia)
    (-> js/window (.matchMedia "(prefers-color-scheme: dark)"))))

(defn onDarkModeChange [f]
  (when dark-mode-matcher
    (.addListener dark-mode-matcher f)))

(defn dark-mode?
  "Checks for prefers-color-scheme: dark. (clj always returns false)"
  []
  (and dark-mode-matcher (.-matches dark-mode-matcher)))


(defrouter PageRouter [this {:keys [current-state]}]
  {:router-targets [login/LoginPage todo-app/MainApp login/SignUpPage]}
  #_(case current-state
      :pending (dom/div "Loading...")
      :failed (dom/div "Failed!")
      ;; default will be used when the current state isn't yet set
      (dom/div "No route selected.")))

(def ui-page-router (comp/factory PageRouter))

(defsc Root [this {:keys [ui/theme ui/page-router]}]
  {:query [:ui/theme
           {:ui/page-router (comp/get-query PageRouter)}
           {:all-proposals (comp/get-query proposal/Proposal)}]
   :initial-state
          (fn [_] {:ui/page-router (comp/get-initial-state PageRouter)
                   :ui/theme       (if (dark-mode?) :dark :light)
                   :all-proposals  []})}
  (styles/theme-provider {:theme (themes/get-mui-theme theme)}
    (mutils/css-baseline {})
    (inj/style-element {:component Root})
    (ui-page-router page-router)))
