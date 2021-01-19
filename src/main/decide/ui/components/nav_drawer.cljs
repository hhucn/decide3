(ns decide.ui.components.nav-drawer
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [decide.application :refer [SPA]]
    [material-ui.data-display :as dd]
    [material-ui.data-display.list :as list]
    [material-ui.layout :as layout]
    [material-ui.navigation :as nav]
    [material-ui.inputs :as inputs]
    ["@material-ui/icons/ChevronLeft" :default ChevronLeftIcon]
    [taoensso.timbre :as log]
    [com.fulcrologic.fulcro.dom :as dom]))

(def ident [:component :navdrawer])

(defmutation toggle-open? [{:keys [open?]}]
  (action [{:keys [state]}]
    (if (some? open?)
      (swap! state update-in ident assoc :ui/open? open?)
      (swap! state update-in ident update :ui/open? not))))

(defn toggle-navdrawer! []
  (comp/transact! SPA [(toggle-open? {:open? nil})]
    {:compressible? true}))

(defsc NavDrawer [this {:keys [ui/open?]}]
  {:query [:ui/open?]
   :ident (fn [] ident)
   :initial-state {:ui/open? false}
   :use-hooks? true}
  (let [on-close (hooks/use-callback #(m/set-value!! this :ui/open? false))]
    (nav/drawer
      {:open open?
       :onClose on-close}
      (layout/box {:width 250
                   :height "100vh"}
        (layout/box {:display :flex
                     :width "100%"
                     :justifyContent "flex-end"
                     :p "4px"}
          (inputs/icon-button {:onClick toggle-navdrawer!}
            (js/React.createElement ChevronLeftIcon nil nil)))
        (dd/divider {:light false})
        (comp/children this)
        (layout/box
          {:width 250
           :bottom 0
           :align "center"}
          (dd/divider {:light false})
          (layout/box {:p 1}
            (dd/typography {:variant "caption"} "decide v3")))))))

(def ui-navdrawer (comp/computed-factory NavDrawer))



