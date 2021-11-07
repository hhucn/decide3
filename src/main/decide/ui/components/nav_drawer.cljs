(ns decide.ui.components.nav-drawer
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [decide.application :refer [SPA]]
    [decide.routes :as routes]
    [decide.ui.components.dark-mode-toggle :as dark-mode-toggle]
    [decide.ui.login :as login]
    [decide.ui.translations.selector :as i18n.switcher]
    [mui.data-display :as dd]
    [mui.data-display.list :as list]
    [mui.inputs :as inputs]
    [mui.layout :as layout]
    [mui.navigation :as nav]
    [mui.surfaces :as surfaces]
    [reitit.frontend.easy :as rfe]
    ["@mui/icons-material/AccountCircle" :default AccountCircle]
    ["@mui/icons-material/ChevronLeft" :default ChevronLeftIcon]
    ["@mui/icons-material/Settings" :default SettingsIcon]
    ["@mui/icons-material/DeviceHub" :default DeviceHubIcon]
    ["@mui/icons-material/Translate" :default TranslateIcon]))

(def ident [:component/id ::NavDrawer])

(defmutation toggle-open? [{:keys [open?]}]
  (action [{:keys [state]}]
    (if (some? open?)
      (swap! state update-in ident assoc :ui/open? open?)
      (swap! state update-in ident update :ui/open? not))))

(defn toggle-navdrawer! []
  (comp/transact! SPA [(toggle-open? {:open? nil})]
    {:compressible? true}))

(defsc NavDrawer [this {:keys [ui/open? ui/locale-switcher ui/dark-mode-toggle]}]
  {:query [:ui/open?
           {:ui/locale-switcher (comp/get-query i18n.switcher/LocaleSwitcher)}
           {:ui/dark-mode-toggle (comp/get-query dark-mode-toggle/DarkModeToggle)}]
   :ident (fn [] ident)
   :initial-state
   (fn [_]
     {:ui/open? false
      :ui/locale-switcher (comp/get-initial-state i18n.switcher/LocaleSwitcher)
      :ui/dark-mode-toggle (comp/get-initial-state dark-mode-toggle/DarkModeToggle)})
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
        (surfaces/toolbar {:width "100%"
                           :sx {:display :flex}}

          (dd/typography {:variant :h5
                          :color "inherit"} "decide")
          (inputs/icon-button
            {:edge :end :onClick toggle-navdrawer!
             :style {:marginLeft :auto}}
            (dom/create-element ChevronLeftIcon)))

        (layout/box {:flexBasis "100%"
                     :my 1}
          (list/list {:component :nav}
            (list/item {:button true
                        :component :a
                        :onClick toggle-navdrawer!
                        :href (rfe/href ::routes/process-list)}
              (list/item-icon {} (dom/create-element DeviceHubIcon))
              (list/item-text {} (i18n/tr "All decisions")))
            (dd/divider {})
            (if (comp/shared this :logged-in?)
              (list/item {:button true
                          :component :a
                          :onClick toggle-navdrawer!
                          :href (rfe/href ::routes/settings)}
                (list/item-icon {} (dom/create-element SettingsIcon))
                (list/item-text {} (i18n/tr "Settings")))
              (list/item
                {:button true
                 :onClick #(comp/transact! this [(login/show-signinup-dialog {:which-form :sign-in})] {})}
                (list/item-icon {} (dom/create-element AccountCircle))
                (list/item-text {} (i18n/tr "Login"))))))

        (layout/stack {:orientation :vertical, :spacing 2, :p 1, :alignItems :center}
          (dark-mode-toggle/ui-dark-mode-toggle dark-mode-toggle)

          (i18n.switcher/ui-language-switcher locale-switcher)

          (dd/typography {:variant :caption
                          :component :footer
                          :color :textSecondary}
            "decide v3"))))))

(def ui-navdrawer (comp/computed-factory NavDrawer))



