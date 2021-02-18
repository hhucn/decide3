(ns decide.ui.components.nav-drawer
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [decide.application :refer [SPA]]
    [decide.routing :as r]
    [decide.ui.pages.settings :as settings]
    [decide.ui.process.list :as process.list]
    [decide.ui.translations.selector :as i18n.switcher]
    [material-ui.data-display :as dd]
    [material-ui.data-display.list :as list]
    [material-ui.inputs :as inputs]
    [material-ui.layout :as layout]
    [material-ui.navigation :as nav]
    [material-ui.surfaces :as surfaces]
    ["@material-ui/icons/ChevronLeft" :default ChevronLeftIcon]
    ["@material-ui/icons/Settings" :default SettingsIcon]
    ["@material-ui/icons/DeviceHub" :default DeviceHubIcon]
    ["@material-ui/icons/Translate" :default TranslateIcon]
    [taoensso.timbre :as log]))

(def ident [:component/id ::NavDrawer])

(defmutation toggle-open? [{:keys [open?]}]
  (action [{:keys [state]}]
    (if (some? open?)
      (swap! state update-in ident assoc :ui/open? open?)
      (swap! state update-in ident update :ui/open? not))))

(defn toggle-navdrawer! []
  (comp/transact! SPA [(toggle-open? {:open? nil})]
    {:compressible? true}))

(defsc NavDrawer [this {:keys [ui/open? ui/locale-switcher]}]
  {:query [:ui/open?
           {:ui/locale-switcher (comp/get-query i18n.switcher/LocaleSwitcher)}]
   :ident (fn [] ident)
   :initial-state
   (fn [_]
     {:ui/open? false
      :ui/locale-switcher (comp/get-initial-state i18n.switcher/LocaleSwitcher)})
   :use-hooks? true}
  (let [on-close (hooks/use-callback #(m/set-value!! this :ui/open? false))]
    (nav/drawer
      {:open open?
       :onClose on-close}
      (layout/box
        {:display :flex
         :flexDirection :column
         :width "250px"
         :height "100vh"}
        (layout/box {:clone true
                     :display :flex}
          (surfaces/toolbar {:width "100%"}

            (dd/typography {:variant :h5
                            :color "inherit"} "decide")
            (inputs/icon-button
              {:edge :end :onClick toggle-navdrawer!
               :style {:marginLeft :auto}}
              (dom/create-element ChevronLeftIcon))))

        (layout/box {:flexBasis "100%"
                     :my 1}
          (list/list {:component :nav}
            (list/item {:button true
                        :component :a
                        :onClick toggle-navdrawer!
                        :href (r/path-to->absolute-url process.list/ProcessesPage)}
              (list/item-icon {} (dom/create-element DeviceHubIcon))
              (list/item-text {} (i18n/tr "All decisions")))
            (dd/divider {})
            (list/item {:button true
                        :component :a
                        :onClick toggle-navdrawer!
                        :href (r/path-to->absolute-url settings/SettingsPage)}
              (list/item-icon {} (dom/create-element SettingsIcon))
              (list/item-text {} (i18n/tr "Settings")))))

        (layout/box {:px 1}
          (i18n.switcher/ui-language-switcher locale-switcher))

        (layout/box
          {:width 250
           :bottom 0
           :align "center"}
          (dd/divider {:light false})
          (layout/box {:p 1 :component :footer}
            (dd/typography {:variant "caption"
                            :color "textSecondary"}
              "decide v3")))))))

(def ui-navdrawer (comp/computed-factory NavDrawer))



