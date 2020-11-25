(ns decide.ui.login
  (:require [material-ui.layout :as layout]
            [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [com.fulcrologic.fulcro.algorithms.form-state :as fs]
            [decide.models.user :as user]
            [material-ui.surfaces :as surfaces]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.dom.events :as evt]
            [material-ui.data-display :as dd]
            [material-ui.utils :as mutils]
            [material-ui.inputs :as inputs]
            [decide.routing :as routing]
            [material-ui.navigation :as navigation]))

(declare LoginPage)

(defn wide-textfield
  "Outlined textfield on full width with normal margins. Takes the same props as `material-ui.inputs/textfield`"
  [props]
  (inputs/textfield
    (merge
      {:variant   :outlined
       :fullWidth true}
      props)))

(defn paper-base [& children]
  (layout/container {:maxWidth "sm"}
    (mutils/css-baseline {})
    (layout/box {:mt 8 :p 3 :clone true}
      (apply surfaces/paper {}
        children))))


(defsc SignUpPage [this {:user/keys [email password]
                         :ui/keys   [email-error password-error]}]
  {:query         [:user/email
                   :ui/email-error

                   :user/password
                   :ui/password-error

                   fs/form-config-join]
   :ident         (fn [] [:page/id :signup])
   :route-segment ["signup"]
   :form-fields   #{:user/email :user/password}
   :initial-state {:user/email        ""
                   :ui/email-error    nil

                   :user/password     ""
                   :ui/password-error nil}}
  (paper-base
    (dom/form
      {:noValidate true
       :onSubmit   (fn submit-sign-up [e]
                     (evt/prevent-default! e)
                     (comp/transact! this [(user/sign-up #:user{:email email :password password})]))}
      (layout/grid {:container true
                    :spacing   2}
        (layout/grid {:item true :xs 12}
          (dd/typography
            {:align   "center"
             :variant "h5"}
            "Sign up"))
        (layout/grid {:item true :xs 12}
          (wide-textfield {:label      "E-Mail"
                           :type       :email
                           :value      email
                           :error      (boolean email-error)
                           :helperText email-error
                           :inputProps {:aria-label   "E-Mail"
                                        :autoComplete :email}
                           :onChange   (fn [e]
                                         (m/set-value!! this :ui/email-error nil)
                                         (m/set-string!! this :user/email :event e))}))
        (layout/grid {:item true :xs 12}
          (wide-textfield {:label      "Password"
                           :type       :password
                           :value      password
                           :error      (boolean password-error)
                           :helperText password-error
                           :inputProps {:aria-label   "Password"
                                        :autoComplete :new-password}
                           :onChange   (fn [e]
                                         (m/set-value!! this :ui/password-error nil)
                                         (m/set-string!! this :user/password :event e))}))
        (layout/grid {:item true :xs 12}
          (inputs/button {:variant   :contained
                          :color     :primary
                          :type      :submit
                          :fullWidth true}
            "Sign up"))
        (layout/grid {:item true}))
      (layout/grid
        {:container true
         :justify   :flex-end}
        (layout/grid {:item true}
          (navigation/link
            (routing/with-route this (dr/path-to LoginPage)
              {:variant :body2})
            "Already have an account? Sign In"))))))


(defsc LoginPage [this {:user/keys [email password]
                        :ui/keys   [email-error password-error]}]
  {:query         [:user/email
                   :ui/email-error

                   :user/password
                   :ui/password-error]
   :ident         (fn [] [:page/id :login])
   :route-segment ["login"]
   :initial-state {:user/email        ""
                   :ui/email-error    nil

                   :user/password     ""
                   :ui/password-error nil}}
  (paper-base
    (dom/form
      {:noValidate true
       :onSubmit   (fn submit-login [e]
                     (evt/prevent-default! e)
                     (comp/transact! this [(user/sign-in #:user{:email    email
                                                                :password password})]))}
      (layout/grid {:container true
                    :spacing   2
                    :direction "column"}
        (layout/grid {:item true :xs 12}
          (dd/typography
            {:align   "center"
             :variant "h5"}
            "Sign in"))
        (layout/grid {:item true :xs 12}
          (wide-textfield {:label      "E-Mail"
                           :type       :email
                           :error      (boolean email-error)
                           :helperText email-error
                           :inputProps {:aria-label   "E-Mail"
                                        :autoComplete :email}
                           :value      email
                           :onChange   (fn [e]
                                         (m/set-value!! this :ui/email-error nil)
                                         (m/set-string!! this :user/email :event e))}))
        (layout/grid {:item true :xs 12}
          (wide-textfield {:label      "Password"
                           :type       :password
                           :error      (boolean password-error)
                           :helperText password-error
                           :inputProps {:aria-label   "Password"
                                        :autoComplete :current-password}
                           :value      password
                           :onChange   (fn [e]
                                         (m/set-value!! this :ui/password-error nil)
                                         (m/set-string!! this :user/password :event e))}))
        (layout/grid {:item true :xs 12}
          (inputs/button {:variant   :contained
                          :color     :primary
                          :type      :submit
                          :fullWidth true}
            "Sign in"))))
    (layout/box {:mt 2 :clone true}
      (layout/grid
        {:container true
         :justify   :space-between}
        (layout/grid {:item true :xs true}
          (navigation/link {:variant :body2} "Forgot password?"))
        (layout/grid {:item true}
          (navigation/link
            (routing/with-route this (dr/path-to SignUpPage)
              {:variant :body2})
            "Don't have an account? Sign Up"))))))