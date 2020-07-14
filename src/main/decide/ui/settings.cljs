(ns decide.ui.settings
  (:require [material-ui.layout :as layout]
            [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
            [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [com.fulcrologic.fulcro.algorithms.form-state :as fs]
            [material-ui.surfaces :as surfaces]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.dom.events :as evt]
            [material-ui.utils :as mutils]
            [material-ui.inputs :as inputs]
            [taoensso.timbre :as log]
            [material-ui.feedback :as feedback]
            [decide.ui.themes :as themes]
            [material-ui.data-display :as dd]))

(defn wide-textfield
  "Outlined textfield on full width with normal margins. Takes the same props as `material-ui.inputs/textfield`"
  [props]
  (inputs/textfield
    (merge
      {:variant   :filled
       :fullWidth true
       :margin    :normal}
      props)))

(defmutation change-password [{:keys [old-password new-password]}]
  (ok-action [{:keys [component] :as env}]
    (let [{:keys [errors]}
          (get-in env [:result :body 'decide.api.user/change-password])]
      (if (empty? errors)
        (m/set-value!! component :ui/success-open? true)
        (cond
          (contains? errors :invalid-credentials)
          (m/set-string!! component :ui/old-password-error :value "Password is wrong.")))))
  (remote [env]
    (m/with-server-side-mutation env 'decide.api.user/change-password)))

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
  (surfaces/paper {}
    (layout/box
      {:component "form"
       :p         3
       :onSubmit  (fn submit-change-password [e]
                    (evt/prevent-default! e)
                    (comp/transact!! this
                      [(m/set-props {:ui/old-password ""
                                     :ui/new-password ""})
                       (change-password {:old-password old-password :new-password new-password})]))}

      (dd/typography
        {:component :h1
         :variant   :h6}
        "Change Password")
      (wide-textfield {:label      "Old Password"
                       :type       :password
                       :error      (boolean old-password-error)
                       :helperText old-password-error
                       :required   true
                       :inputProps {:aria-label   "Old Password"
                                    :autoComplete :current-password}
                       :value      old-password
                       :onChange   (fn [e]
                                     (m/set-value!! this :ui/old-password-error nil)
                                     (m/set-string!! this :ui/old-password :event e))})
      (wide-textfield {:label      "New Password"
                       :type       :password
                       :error      (boolean new-password-error)
                       :helperText new-password-error
                       :required   true
                       :value      new-password
                       :inputProps {:minLength    10
                                    :aria-label   "New Password"
                                    :autoComplete :new-password}

                       :onChange   (fn [e]
                                     (m/set-value!! this :ui/new-password-error nil)
                                     (m/set-string!! this :ui/new-password :event e))})
      (layout/box {:mt 2}
        (inputs/button
          {:color   :primary
           :variant :contained
           :margin  "normal"
           :type    :submit}
          "Save")))
    (feedback/snackbar
      {:autoHideDuration 6000
       :open             success-open?
       :onClose          #(m/set-value!! this :ui/success-open? false)
       :message          "Password changed"})))

(def ui-new-password-form (comp/factory NewPasswordForm))

(defsc SettingsPage [_this {:ui/keys [new-password-form]}]
  {:query         [{:ui/new-password-form (comp/get-query NewPasswordForm)}]
   :ident         (fn [] [:page/id :settings])
   :route-segment ["settings"]
   :initial-state (fn [_] {:ui/new-password-form (comp/get-initial-state NewPasswordForm)})}
  (layout/container
    {:maxWidth "lg"}
    (layout/grid
      {:direction :column
       :container true
       :spacing   2
       :justify   "flex-start"}
      (layout/grid {:item true
                    :xs   12}
        (ui-new-password-form new-password-form)))))
