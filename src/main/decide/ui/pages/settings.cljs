(ns decide.ui.pages.settings
  (:require
    [clojure.set :as set]
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [decide.models.user :as user]
    [decide.models.user.api :as user.api]
    [decide.models.user.ui :as user.ui]
    [decide.ui.components.snackbar :as snackbar]
    [material-ui.feedback :as feedback]
    [material-ui.inputs :as inputs]
    [material-ui.lab :refer [skeleton]]
    [material-ui.layout :as layout]
    [material-ui.layout.grid :as grid]
    [material-ui.surfaces :as surfaces]))

(def settings-page-ident [:SCREEN ::settings])

(defn wide-textfield
  "Outlined textfield on full width with normal margins. Takes the same props as `material-ui.inputs/textfield`"
  [props]
  (inputs/textfield
    (merge
      {:variant :outlined
       :fullWidth true
       :margin :normal}
      props)))

(defn save-button []
  (inputs/button
    {:color :primary
     :type :submit}
    (i18n/tr "Save")))

(defsc NewPasswordForm [this {:ui/keys [old-password new-password
                                        old-password-error new-password-error
                                        success-open?]}]
  {:query [:ui/old-password :ui/new-password :ui/old-password-error :ui/new-password-error :ui/success-open? fs/form-config-join]
   :ident (fn [] [:component/id :settings/new-password-form])
   :form-fields [:ui/old-password :ui/new-password]
   :initial-state #:ui{:old-password ""
                       :new-password ""
                       :old-password-error nil
                       :new-password-error nil
                       :ui/success-open? false}}
  (surfaces/card
    {:component :form
     :onSubmit (fn submit-change-password [e]
                 (evt/prevent-default! e)
                 (comp/transact!! this
                   [(m/set-props {:ui/old-password ""
                                  :ui/new-password ""})
                    (user/change-password {:old-password old-password :new-password new-password})]))}
    (surfaces/card-header {:title (i18n/tr "Change Password")})
    (surfaces/card-content {}
      (wide-textfield {:label (i18n/tr "Old password")
                       :type :password
                       :error (boolean old-password-error)
                       :helperText old-password-error
                       :required true
                       :inputProps {:aria-label (i18n/tr "Old password")
                                    :autoComplete :current-password}
                       :value old-password
                       :onChange (fn [e]
                                   (m/set-value!! this :ui/old-password-error nil)
                                   (m/set-string!! this :ui/old-password :event e))})
      (wide-textfield {:label (i18n/tr "New password")
                       :type :password
                       :error (boolean new-password-error)
                       :helperText new-password-error
                       :required true
                       :value new-password
                       :inputProps {:minLength 10
                                    :aria-label (i18n/tr "New password")
                                    :autoComplete :new-password}

                       :onChange (fn [e]
                                   (m/set-value!! this :ui/new-password-error nil)
                                   (m/set-string!! this :ui/new-password :event e))}))
    (surfaces/card-actions {}
      (save-button))
    (feedback/snackbar
      {:autoHideDuration 6000
       :open success-open?
       :onClose #(m/set-value! this :ui/success-open? false)
       :message (i18n/tr "Password changed")})))

(def ui-new-password-form (comp/factory NewPasswordForm))

(declare UserInformation)

(defmutation save-user-info [user]
  (remote [env]
    (-> env
      (m/with-params (set/rename-keys (select-keys user [::user/id :user/display-name :user/email])
                       {:user/display-name ::user/display-name}))
      (m/with-server-side-mutation `user.api/update-user)
      (m/returning UserInformation)))
  (ok-action [{:keys [app]}]
    (comp/transact! app [(snackbar/add {:message (i18n/tr "Personal details saved")})])))

(defn valid-display-name? [display-name]                    ; TODO move to user ns once it is cljc
  (< 2 (count display-name) 50))

(defmutation undo-update [user]
  (action [{:keys [app state]}]
    (when (contains? user ::user/id)
      (comp/transact! app [(user.api/update-user user)])
      (swap! state fs/entity->pristine* (find user ::user/id)))))

(defn user-info-valid? [form field]
  (let [v (get form field)]
    (case field
      ::user/display-name (valid-display-name? v)           ; not empty
      true)))

(def user-info-validator (fs/make-validator user-info-valid?))

(defsc UserInformation [this {::user/keys [id display-name]
                              :keys [>/avatar user/email user/nickname]
                              :as props}]
  {:ident ::user/id
   :query [::user/id
           ::user/display-name
           :user/email
           :user/nickname
           {:>/avatar (comp/get-query user.ui/Avatar)}
           fs/form-config-join]
   :form-fields #{::user/display-name :user/email}
   :pre-merge (fn [{:keys [current-normalized data-tree]}]
                (fs/add-form-config UserInformation (merge current-normalized data-tree) {:destructive? true}))
   :componentWillUnmount
   (fn [this]
     (comp/transact! this [(fs/reset-form! {:form-ident (comp/get-ident this)})]))}

  (surfaces/card
    {:component :form
     :onSubmit
     (fn [e]
       (evt/prevent-default! e)
       (when (fs/dirty? props)
         (let [dirty-state (->
                             (fs/dirty-fields props false)
                             (get (comp/get-ident this)))
               dirty-keys (keys dirty-state)]
           (comp/transact! this
             [(user.api/update-user (assoc dirty-state ::user/id id))
              (snackbar/add
                {:message (i18n/tr "Details updated")
                 :action {:label (i18n/tr "Undo")
                          :mutation `user.api/update-user
                          :mutation-params
                          (let [empty-map (into {} (zipmap (get-in props [::fs/config ::fs/fields]) (repeat "")))
                                undo-state (merge empty-map (get-in props [::fs/config ::fs/pristine-state]))]
                            (-> undo-state
                              (select-keys dirty-keys)
                              (assoc ::user/id id)))}})]
             {:optimistic? false}))))}

    (surfaces/card-header {:title (i18n/tr "Personal Details")})

    (surfaces/card-content {}
      (grid/container {:spacing 1}
        (grid/item {}
          (user.ui/ui-avatar avatar {:avatar-props {:style {:width "70px" :height "70px" :fontSize "3rem"}}}))
        (wide-textfield {:label (i18n/tr "Display name")
                         :value display-name
                         :error (= :invalid (user-info-validator props ::user/display-name))
                         :onChange #(m/set-string!! this ::user/display-name :event %)
                         :onBlur #(comp/transact! this [(fs/mark-complete! {:field ::user/display-name})])
                         :inputProps {:minLength 1}})
        (wide-textfield {:label (i18n/tr "Nickname")
                         :value (or nickname "")
                         :disabled true
                         :error (= :invalid (user-info-validator props :user/nickname))
                         :helperText (i18n/trc "Nickname" "Public, unique identifier")
                         :inputProps {:minLength 4 :maxLength 15}})
        (wide-textfield
          {:label (i18n/tr "Email")
           :value (or email "")
           :type :email
           :error (= :invalid (user-info-validator props :user/email))
           :helperText (i18n/trc "Email field" "This is private. Leave empty if you don't want notification mails.")
           :onBlur #(comp/transact! this [(fs/mark-complete! {:field :user/email})])
           :onChange #(m/set-string!! this :user/email :event %)})))

    (surfaces/card-actions {}
      (inputs/button
        {:color :primary
         :type :submit
         :disabled (not (fs/dirty? props))}
        (i18n/tr "Save")))))

(def ui-user-panel (comp/factory UserInformation))

(defmutation init-user-info-panel [{::user/keys [id]}]
  (action [{:keys [app]}]
    (df/load! app [::user/id id] UserInformation
      {:marker ::load-user-information
       :target (conj settings-page-ident :ui/user-information)})))


(defmutation init-settings-page [_]
  (action [{:keys [state app]}]
    (when-let [user-ident (get-in @state [:root/current-session :user])]
      (comp/transact! app [(init-user-info-panel {::user/id (second user-ident)})]))))

(defsc SettingsPage [_this {:ui/keys [new-password-form user-information] :as props}]
  {:query [{:ui/new-password-form (comp/get-query NewPasswordForm)}
           {:ui/user-information (comp/get-query UserInformation)}
           [df/marker-table ::load-user-information]]
   :ident (fn [] settings-page-ident)
   :route-segment ["settings"]
   :will-enter
   (fn [app]
     (dr/route-deferred settings-page-ident
       #(comp/transact! app [(init-settings-page {})
                             (dr/target-ready {:target settings-page-ident})])))
   :initial-state (fn [_] {:ui/new-password-form (comp/get-initial-state NewPasswordForm)})}
  (let [user-information-marker (get props [df/marker-table ::load-user-information])]
    (layout/box {:mt 2}
      (layout/container
        {:maxWidth "lg"}
        (grid/container
          {:direction :column
           :spacing 2
           :justifyContent "flex-start"}
          (grid/item {}
            (cond
              user-information (ui-user-panel user-information)
              (df/loading? user-information-marker) (skeleton {:variant "rect" :height "401px"})))
          (grid/item {:xs 12}
            (ui-new-password-form new-password-form)))))))
