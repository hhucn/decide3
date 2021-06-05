(ns decide.ui.pages.settings
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.mutations :as m]
    [decide.models.user :as user]
    [material-ui.data-display :as dd]
    [material-ui.feedback :as feedback]
    [material-ui.inputs :as inputs]
    [material-ui.layout :as layout]
    [material-ui.layout.grid :as grid]
    [material-ui.surfaces :as surfaces]))

(defn wide-textfield
  "Outlined textfield on full width with normal margins. Takes the same props as `material-ui.inputs/textfield`"
  [props]
  (inputs/textfield
    (merge
      {:variant   :filled
       :fullWidth true
       :margin    :normal}
      props)))

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
      (inputs/button
        {:color :primary
         :margin "normal"
         :type :submit}
        (i18n/tr "Save")))
    (feedback/snackbar
      {:autoHideDuration 6000
       :open success-open?
       :onClose #(m/set-value! this :ui/success-open? false)
       :message (i18n/tr "Password changed")})))

(def ui-new-password-form (comp/factory NewPasswordForm))

(defsc SettingsPage [_this {:ui/keys [new-password-form]}]
  {:query         [{:ui/new-password-form (comp/get-query NewPasswordForm)}]
   :ident         (fn [] [:SCREEN :settings])
   :route-segment ["settings"]
   :initial-state (fn [_] {:ui/new-password-form (comp/get-initial-state NewPasswordForm)})}
  (layout/container
    {:maxWidth "lg"}
    (grid/container
      {:direction :column
       :spacing 2
       :justify "flex-start"}
      (grid/item {:xs 12}
        (ui-new-password-form new-password-form)))))
