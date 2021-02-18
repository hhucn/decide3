(ns decide.ui.components.appbar
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [decide.models.authorization :as auth]
    [decide.models.user :as user]
    [decide.ui.login :as login]
    [material-ui.data-display :as dd]
    [material-ui.inputs :as inputs]
    [material-ui.layout :as layout]
    [material-ui.navigation :as navigation]
    [material-ui.surfaces :as surfaces]
    ["@material-ui/icons/AccountCircle" :default AccountCircleIcon]
    ["@material-ui/icons/Menu" :default Menu]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.dom :as dom]))

(def appbar-theme-color
  {:light "primary"
   :dark "inherit"})

(defsc AppBar
  [this
   {:keys [ui/account-menu-open? ui/theme root/current-session]}
   {:keys [menu-onClick]}]
  {:query [:ui/account-menu-open?
           [:ui/theme '_]
           {[:root/current-session '_] (comp/get-query auth/Session)}]
   :ident (fn [] [:component/id ::AppBar])
   :initial-state {:ui/account-menu-open? false}
   :use-hooks? true}
  (let [logged-in? (get current-session :session/valid?)
        display-name (get-in current-session [:user ::user/display-name])
        menu-ref (hooks/use-ref)
        [easteregg-count set-easteregg-count!] (hooks/use-state 0)
        show-easteregg? (and (zero? (mod easteregg-count 5)) (pos? easteregg-count))

        [temp-nickname set-temp-nickname] (hooks/use-state "")]
    (surfaces/app-bar
      {:position "sticky"
       :color (appbar-theme-color theme)
       :elevation (if (= :light theme) 2 0)}
      (surfaces/toolbar {}
        (when menu-onClick
          (inputs/icon-button
            {:edge :start
             :color :inherit
             :aria-label (i18n/trc "[aria] navigation menu" "navigation menu")
             :onClick menu-onClick}
            (layout/box {:component Menu})))
        (dd/typography
          {:component :span
           :variant :h5
           :color "inherit"
           :onClick #(set-easteregg-count! (inc easteregg-count))}
          (if show-easteregg?
            "d-cider 🍾"
            "decide"))

        ; Spacer
        (layout/box {:display :flex
                     :flexGrow 1
                     :alignItems :center
                     :flexDirection :row-reverse}

          (if-not logged-in?
            (dom/form
              {:onSubmit #(comp/transact! this [(login/sign-in #:decide.models.user{:email temp-nickname :password temp-nickname})])}
              (inputs/textfield
                {:variant :outlined
                 :size :small
                 :value temp-nickname
                 :onChange #(set-temp-nickname (evt/target-value %))
                 :label (i18n/trc "Temp Nickname for login" "Nickname")
                 :InputProps
                 {:endAdornment
                  (inputs/button
                    {:variant :text
                     :color :inherit
                     :type :submit}
                    ; :onClick #(comp/transact! this [(login/show-signinup-dialog {:which-form :sign-in})] {:compressible? true})}
                    (i18n/trc "Label of login button" "Login"))}}))


            (comp/fragment

              (inputs/icon-button
                {:ref menu-ref
                 :edge "end"
                 :aria-label (i18n/trc "[aria]" "account of current user")
                 :aria-controls "menuId"
                 :aria-haspopup true
                 :onClick #(m/set-value! this :ui/account-menu-open? true)
                 :color "inherit"}
                (layout/box {:component AccountCircleIcon}))

              (layout/box {:p 1}
                (dd/typography {:color :inherit} display-name))

              (navigation/menu
                {:keepMounted true
                 :id "menuId"
                 :anchorEl (.-current menu-ref)
                 :getContentAnchorEl nil
                 :anchorOrigin {:vertical "bottom"
                                :horizontal "left"}
                 :transformOrigin {:vertical "top"
                                   :horizontal "center"}
                 :open account-menu-open?
                 :onClose #(m/set-value! this :ui/account-menu-open? false)}
                (navigation/menu-item {:component :a
                                       :href "/settings"}
                  (i18n/tr "Settings"))
                (navigation/menu-item {:onClick #(comp/transact! this [(user/sign-out nil)])}
                  (i18n/trc "Label of logout button" "Logout"))))))))))

(def ui-appbar (comp/computed-factory AppBar))