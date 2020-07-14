(ns decide.ui.sidedrawer
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [material-ui.data-display :as dd]
            [material-ui.layout :as layout]
            [material-ui.navigation :as navigation]
            [material-ui.surfaces :as surfaces :refer [toolbar paper]]
            [decide.routing :as routing]
            [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
            [decide.ui.settings :as settings]
            [taoensso.timbre :as log]
            [com.fulcrologic.fulcro.ui-state-machines]
            [decide.ui.themes :as themes]
            [com.fulcrologic.fulcro-css.css :as css]))

(defmutation toggle-drawer [{:keys [open?]}]
  (action [{:keys [state ref]}]
    (let [nav-drawer-target (vec (concat ref [:ui/nav-drawer :ui/open?]))]
      (if (nil? open?)
        (swap! state update-in nav-drawer-target not)
        (swap! state assoc-in nav-drawer-target open?)))))

(defn open-drawer! [comp] (comp/transact! comp [(toggle-drawer {:open? true})] {:compressible? true}))
(defn close-drawer! [comp] (comp/transact! comp [(toggle-drawer {:open? false})] {:compressible? true}))
(defn toggle-drawer! [comp] (comp/transact! comp [(toggle-drawer {})] {:compressible? true}))

(defn list-item-link [props & children]
  (apply
    dd/list-item
    (merge
      {:button    true
       :component "a"}
      props)
    children))

(defn nav-entry [component {:keys [label path css]}]
  (let [{:keys [active]} css]
    (list-item-link
      {:href      (routing/path->url path)
       :className (when (= (dr/current-route component) path) active)}
      (dd/list-item-text
        {:primary label}))))

(defsc NavDrawer [this {:keys [ui/open?] :or {open? false}} _ css]
  {:query         [:ui/open?
                   [:com.fulcrologic.fulcro.ui-state-machines/asm-id :decide.ui.todo-app/ContentRouter]]
   :initial-state {:ui/open? false}
   :css           [[:.active {:color            (get-in themes/light-theme [:palette :primary :main])
                              ;;                                                                          ~12% opacity
                              :background-color (str (get-in themes/light-theme [:palette :primary :main]) "1E")}]]}
  (let [drawer
        (comp/fragment
          (surfaces/toolbar)
          (dom/div {:style {:height "4px"}})
          (dd/list {}
            (nav-entry this
              {:label "Vorschläge"
               :path  (dr/path-to
                        (comp/registry-key->class 'decide.ui.main-app/MainApp)
                        (comp/registry-key->class 'decide.ui.main-app/MainProposalList))
               :css   css})
            (dd/divider {})
            (nav-entry this
              {:label "Einstellungen"
               :path  (dr/path-to
                        (comp/registry-key->class 'decide.ui.main-app/MainApp)
                        settings/SettingsPage)
               :css   css})))]
    (comp/fragment
      (layout/hidden
        {:smUp true}
        (navigation/swipeable-drawer
          {:anchor  :left
           :open    open?
           :onOpen  #(open-drawer! this)
           :onClose #(close-drawer! this)
           :PaperProps
                    {:style     {:width 240}
                     :component :aside}}
          drawer))

      (layout/hidden
        {:xsDown true}
        (navigation/swipeable-drawer
          {:anchor  :left
           :variant :persistent
           :open    open?
           :onOpen  #(open-drawer! this)
           :onClose #(close-drawer! this)
           :PaperProps
                    {:style     {:width 240}
                     :component :aside}}
          drawer)))))

(def ui-nav-drawer (comp/computed-factory NavDrawer))