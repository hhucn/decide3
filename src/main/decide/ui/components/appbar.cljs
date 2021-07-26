(ns decide.ui.components.appbar
  (:require
    [clojure.string :as str]
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.algorithms.normalized-state :as norm-state]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.raw.components :as rc]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [decide.models.authorization :as auth]
    [decide.models.user :as user]
    [decide.models.user.api :as user.api]
    [decide.ui.components.snackbar :as snackbar]
    [decide.ui.login :as login]
    [material-ui.data-display :as dd]
    [material-ui.feedback.dialog :as dialog]
    [material-ui.inputs :as inputs]
    [material-ui.inputs.form :as form]
    [material-ui.layout :as layout]
    [material-ui.navigation :as navigation]
    [material-ui.surfaces :as surfaces]
    [material-ui.transitions :as transitions]
    ["@material-ui/icons/AccountCircle" :default AccountCircleIcon]
    ["@material-ui/icons/HelpOutline" :default HelpIcon]
    ["@material-ui/icons/Menu" :default Menu]
    ["@material-ui/icons/Notifications" :default Notifications]))

(defmutation open-dialog [{:keys [id]}]
  (action [{:keys [state]}]
    (swap! state update-in [:dialog/id id] assoc :ui/open? true)))

(declare AddEmailForNotificationDialog)

(defmutation initialize-notification-dialog [params]
  (action [{:keys [app state]}]
    (let [id (norm-state/get-in-graph @state [:root/current-session :user ::user/id])]
      (df/load! app [::user/id id] (rc/nc [::user/id :user/email])
        {:marker ::load-user-email
         :post-action
         (fn [{:keys [state]}]
           (let [email (norm-state/get-in-graph @state [:root/current-session :user :user/email])]
             (when-not (str/blank? email)
               (norm-state/swap!-> state
                 (update-in [:dialog/id ::AddEmailForNotificationDialog]
                   assoc
                   :user/email email
                   :ui/receive-notifications? true)
                 (fs/add-form-config* AddEmailForNotificationDialog [:dialog/id ::AddEmailForNotificationDialog] {:destructive? true})))))}))))

(defsc AddEmailForNotificationDialog [this {:keys [ui/open? ui/receive-notifications? user/email] :as props}]
  {:query [:ui/open?
           :ui/receive-notifications?
           :user/email
           fs/form-config-join]
   :form-fields #{:ui/receive-notifications? :user/email}
   :initial-state
   {:ui/open? false
    :ui/receive-notifications? false
    :user/email ""}
   :ident (fn [] [:dialog/id ::AddEmailForNotificationDialog])}
  (dialog/dialog
    {:open open?
     :onClose #(m/set-value! this :ui/open? false)
     :PaperProps
     {:component :form
      :onSubmit
      (fn [e]
        (evt/prevent-default! e)
        (when (fs/dirty? props)
          (comp/transact! this
            [(user.api/update-current {:user/email (if receive-notifications? email "")})
             (m/set-props {:ui/open? false})
             (snackbar/add
               {:message "Email saved"
                :action {:label (i18n/tr "Undo")
                         :mutation `user.api/update-current
                         :mutation-params {:user/email (get-in props [::fs/config ::fs/pristine-state :user/email])}}})])))}}
    (dialog/title {} (i18n/tr "Receive notifications"))
    (dialog/content {}
      (dialog/content-text {}
        (i18n/tr "Get notifications for processes you participate in when something happened. You get at most one email per hour."))
      (form/control-label
        {:label (i18n/tr "Receive notifications")
         :checked receive-notifications?
         :onChange #(m/set-value! this :ui/receive-notifications? (.-checked (.-target %)))
         :control (inputs/checkbox {})})
      (transitions/collapse {:in receive-notifications?}
        (inputs/textfield
          {:label (i18n/tr "Email")
           :value email
           :type :email
           :variant :outlined
           :fullWidth true
           :margin :normal
           :onChange #(m/set-string!! this :user/email :event %)})))
    (dialog/actions {}
      (inputs/button {:onClick #(m/set-value! this :ui/open? false)}
        (i18n/tr "Cancel"))
      (inputs/button {:color :primary
                      :type :submit
                      :disabled (not (fs/dirty? props))}
        (i18n/tr "Done")))))

(def ui-add-email-for-notification-dialog (comp/factory AddEmailForNotificationDialog))

(defsc AppBar
  [this
   {:keys [ui/account-menu-open? root/current-session ui/notification-mail-dialog]}
   {:keys [menu-onClick]}]
  {:query [:ui/account-menu-open?
           {[:root/current-session '_] (comp/get-query auth/Session)}
           {:ui/notification-mail-dialog (comp/get-query AddEmailForNotificationDialog)}]
   :ident (fn [] [:component/id ::AppBar])
   :initial-state
   {:ui/account-menu-open? false
    :ui/notification-mail-dialog {:ui/open? false}}

   :use-hooks? true}
  (let [logged-in? (get current-session :session/valid?)
        display-name (get-in current-session [:user ::user/display-name])
        menu-ref (hooks/use-ref)
        [easteregg-count set-easteregg-count!] (hooks/use-state 0)
        show-easteregg? (and (zero? (mod easteregg-count 5)) (pos? easteregg-count))

        [temp-nickname set-temp-nickname] (hooks/use-state "")]
    (surfaces/app-bar
      {:position "sticky"
       :color "primary"}
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
              {:onSubmit (fn [e]
                           (evt/prevent-default! e)
                           (comp/transact! this [(login/sign-in #:decide.models.user{:email temp-nickname :password temp-nickname})]))}
              (inputs/textfield
                {:variant :filled
                 :name :username
                 :size :small
                 :required true
                 :value temp-nickname
                 :onChange #(set-temp-nickname (str/replace (evt/target-value %) #"\s" ""))
                 :label (i18n/trc "Temp Nickname for login" "Nickname")
                 :style {:color "inherit"}
                 :InputLabelProps {:style {:color :inherit}}
                 :InputProps
                 {:style {:color :inherit
                          :border "1px solid rgba(255,255,255,0.77)"}
                  :endAdornment
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
                  (i18n/trc "Label of logout button" "Logout")))))

          (when logged-in?
            (comp/fragment
              (inputs/icon-button {:color :inherit
                                   :onClick #(comp/transact! this [(initialize-notification-dialog {})
                                                                   (open-dialog {:id ::AddEmailForNotificationDialog})])}
                (dom/create-element Notifications))
              (ui-add-email-for-notification-dialog notification-mail-dialog)))

          (inputs/icon-button {:color :inherit
                               :component :a
                               :href "/help"}
            (dom/create-element HelpIcon)))))))

(def ui-appbar (comp/computed-factory AppBar))