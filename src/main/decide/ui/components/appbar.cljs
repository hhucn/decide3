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
    [decide.models.user :as user]
    [decide.models.user.api :as user.api]
    [decide.ui.components.snackbar :as snackbar]
    [decide.ui.login :as login]
    [mui.data-display :as dd]
    [mui.feedback.dialog :as dialog]
    [mui.inputs :as inputs]
    [mui.inputs.form :as form]
    [mui.layout :as layout]
    [mui.navigation :as navigation]
    [mui.surfaces :as surfaces]
    [mui.transitions :as transitions]
    ["@mui/icons-material/AccountCircle" :default AccountCircleIcon]
    ["@mui/icons-material/HelpOutline" :default HelpIcon]
    ["@mui/icons-material/Menu" :default Menu]
    ["@mui/icons-material/Notifications" :default Notifications]))

(defmutation open-dialog [{:keys [id]}]
  (action [{:keys [state]}]
    (swap! state update-in [:dialog/id id] assoc :ui/open? true)))

;; region Account Menu

(defmutation toggle-menu [{:keys [menu/id]}]
  (action [{:keys [state]}]
    (swap! state update-in [:menu/id id :ui/open?] not)))

(defsc AccountMenu [this {:keys [menu/id ui/open?]} {:keys [ref]}]
  {:query [:menu/id :ui/open?]
   :ident :menu/id
   :initial-state
   {:menu/id :param/id
    :ui/open? false}}
  (letfn [(toggle [] (comp/transact! this [(toggle-menu {:menu/id id})]
                       {:compressible? true
                        :only-refresh [(comp/get-ident this)]}))]
    (navigation/menu
      {:id id
       :open open?
       :onClose toggle

       :keepMounted true
       :anchorEl (.-current ref)
       :transformOrigin {:vertical :top, :horizontal :center}}
      (navigation/menu-item {:component :a
                             :href "/settings"
                             :onClick toggle}
        (i18n/tr "Settings"))
      (navigation/menu-item {:onClick #(comp/transact! this [(user/sign-out nil)
                                                             (toggle-menu {:menu/id id})])}
        (i18n/trc "Label of logout button" "Logout")))))

(def ui-account-menu (comp/computed-factory AccountMenu))

(defsc AccountMenuButton [_ {::user/keys [display-name]} {:keys [onClick ref]}]
  {:query [::user/id ::user/display-name]
   :ident ::user/id}
  (inputs/button
    {:ref ref
     :title (i18n/tr "Open account menu")
     :color :inherit
     :onClick onClick
     :endIcon (dom/create-element AccountCircleIcon)}
    display-name))

(def ui-account-menu-button (comp/computed-factory AccountMenuButton))

(defsc AccountDisplay [this {:keys [ui/account-menu] :as props}]
  {:query [{[:root/current-session :user] (comp/get-query AccountMenuButton)}
           {:ui/account-menu (comp/get-query AccountMenu)}]
   :initial-state {:ui/account-menu {:id ::account-menu}}
   :use-hooks? true}
  (let [menu-ref (hooks/use-ref)
        menu-id (:menu/id account-menu)]
    (comp/fragment
      (ui-account-menu-button (get props [:root/current-session :user])
        {:ref menu-ref
         :onClick #(comp/transact! this [(toggle-menu {:menu/id menu-id})]
                     {:compressible? true
                      :only-refresh [(comp/get-ident AccountMenu account-menu)]})})
      (ui-account-menu account-menu {:ref menu-ref}))))

(def ui-account-display (comp/factory AccountDisplay))
;; endregion

;; region AddEmailDialog
(declare AddEmailForNotificationDialog)
(def email-notification-dialog-ident [:dialog/id ::AddEmailForNotificationDialog])

(defmutation initialize-notification-dialog [params]
  (action [{:keys [app state]}]
    (let [id (norm-state/get-in-graph @state [:root/current-session :user ::user/id])]
      (df/load! app [::user/id id] (rc/nc [::user/id :user/email])
        {:marker ::load-user-email
         :post-action
         (fn [{:keys [state]}]
           (let [email (norm-state/get-in-graph @state [:root/current-session :user :user/email])]
             (if (str/blank? email)
               (norm-state/swap!-> state
                 (update-in email-notification-dialog-ident
                   assoc
                   :user/email ""
                   :ui/receive-notifications? false)
                 (fs/add-form-config* AddEmailForNotificationDialog email-notification-dialog-ident {:destructive? true}))
               (norm-state/swap!-> state
                 (update-in email-notification-dialog-ident
                   assoc
                   :user/email email
                   :ui/receive-notifications? true)
                 (fs/add-form-config* AddEmailForNotificationDialog email-notification-dialog-ident {:destructive? true})))))}))))

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
   :ident (fn [] email-notification-dialog-ident)}
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
        (i18n/tr "Get notifications when something changes. You will receive at most one email per hour."))
      (form/control-label
        {:label (i18n/tr "Receive notifications")
         :checked receive-notifications?
         :onChange #(comp/transact! this [(m/set-props {:ui/receive-notifications? (.-checked (.-target %))})]
                      {:only-refresh [email-notification-dialog-ident]})
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

(defsc AddEmailButton [this {:keys [ui/notification-mail-dialog]}]
  {:query [{:ui/notification-mail-dialog (comp/get-query AddEmailForNotificationDialog)}]
   :initial-state (fn [_] {:ui/notification-mail-dialog {:ui/open? false}})}
  (comp/fragment
    (inputs/icon-button {:color :inherit
                         :onClick #(comp/transact! this [(initialize-notification-dialog {})
                                                         (open-dialog {:id ::AddEmailForNotificationDialog})]
                                     {:only-refresh [[:dialog/id ::AddEmailForNotificationDialog]]})}
      (dom/create-element Notifications))
    (ui-add-email-for-notification-dialog notification-mail-dialog)))

(def ui-add-email-button (comp/factory AddEmailButton))
;; endregion

(defsc Logo [_ _]
  {:use-hooks? true}
  (let [[easteregg-count set-easteregg-count!] (hooks/use-state 0)
        easteregg? (and (zero? (mod easteregg-count 5)) (pos? easteregg-count))]
    (dd/typography
      {:component :span
       :variant :h5
       :color :inherit
       :onClick #(set-easteregg-count! (inc easteregg-count))}
      (if easteregg?
        "d-cider 🍾"
        "decide"))))

(def ui-logo (comp/factory Logo))

(defsc AppBar
  [this
   {:keys [ui/account-display ui/add-email-dialog]}
   {:keys [menu-onClick]}]
  {:query [{:ui/account-display (comp/get-query AccountDisplay)}
           {:ui/add-email-dialog (comp/get-query AddEmailButton)}]
   :ident (fn [] [:component/id ::AppBar])
   :initial-state
   (fn [_]
     {:ui/add-email-dialog (comp/get-initial-state AddEmailButton)
      :ui/account-display (comp/get-initial-state AccountDisplay)})
   :use-hooks? true}
  (let [logged-in? (comp/shared this :logged-in?)]
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
            (dom/create-element Menu)))
        (ui-logo {})

        ; Spacer
        (layout/stack {:direction :row
                       :ml :auto}

          (inputs/icon-button {:color :inherit
                               :component :a
                               :href "/help"}
            (dom/create-element HelpIcon))

          (when logged-in?
            (ui-add-email-button add-email-dialog))

          (if-not logged-in?
            (inputs/button
              {:variant :outlined
               :color :inherit
               :onClick #(comp/transact! this [(login/show-signinup-dialog {:which-form :sign-in})] {:compressible? true})}
              (i18n/trc "Label of login button" "Login"))

            (ui-account-display account-display)))))))

(def ui-appbar (comp/computed-factory AppBar))