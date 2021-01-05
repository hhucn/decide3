(ns decide.ui.login
  (:require [material-ui.layout :as layout]
            [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [com.fulcrologic.fulcro.algorithms.form-state :as fs]
            [decide.models.process :as process]
            [decide.models.user :as user]
            [decide.ui.proposal.main-proposal-list :as main-proposal-list]
            [material-ui.surfaces :as surfaces]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.dom.events :as evt]
            [material-ui.data-display :as dd]
            [material-ui.utils :as mutils]
            [material-ui.inputs :as inputs]
            [material-ui.layout.grid :as grid]
            [decide.routing :as routing]
            [material-ui.navigation :as navigation]
            [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
            [taoensso.timbre :as log]))

(declare LoginPage)

(defn reset-password-field! [component]
  (m/set-string! component ::user/password :value ""))

(defn redirect-to-main-list! [component]
  (comp/transact!! component [(routing/route-to {:path (dr/path-to main-proposal-list/MainProposalList {::process/slug "test-decision"})})]))

(defmutation sign-up [{:user/keys [_email _password]}]
  (action [_] true)
  (ok-action [{:keys [component result] :as env}]
    (let [{:keys [errors]} (get-in env [:result :body `user/sign-up])]
      (if (empty? errors)
        (do
          (reset-password-field! component)
          (redirect-to-main-list! component))
        (cond
          (contains? errors :email-in-use)
          (m/set-string! component :ui/email-error :value "Email already in use!")))))
  (remote [env]
    (-> env
      (m/with-server-side-mutation `user/sign-up)
      (m/returning user/Session))))

(defmutation sign-in [{:user/keys [_email _password]}]
  (action [_] true)
  (ok-action [{:keys [component] :as env}]
    (let [{:keys [errors]}
          (get-in env [:result :body `user/sign-in])]
      (if (empty? errors)
        (do
          (log/info "Login: ok")
          (reset-password-field! component)
          (redirect-to-main-list! component))
        (when errors
          (cond
            (or
              (contains? errors :account-does-not-exist)
              (contains? errors :invalid-credentials))
            (m/set-string!! component :ui/password-error :value "Email or password is wrong."))))))
  (remote [env]
    (-> env
      (m/with-server-side-mutation `user/sign-in)
      (m/returning user/Session))))

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

(defsc SignUpPage [this {:keys [::user/email ::user/password]
                         :ui/keys [email-error password-error]}]
  {:query [::user/email
           :ui/email-error

           ::user/password
           :ui/password-error

           fs/form-config-join]
   :ident (fn [] [:SCREEN :sign-up-in])
   :route-segment ["signup"]
   :form-fields #{::user/email ::user/password}
   :initial-state {::user/email ""
                   :ui/email-error nil

                   ::user/password ""
                   :ui/password-error nil}}
  (paper-base
    (dom/form
      {:noValidate true
       :onSubmit (fn submit-sign-up [e]
                   (evt/prevent-default! e)
                   (comp/transact! this [(sign-up #:user{::user/email email ::user/password password})]))}
      (grid/container {:spacing 2}
        (grid/item {:xs 12}
          (dd/typography
            {:align "center"
             :variant "h5"}
            "Sign up"))
        (grid/item {:xs 12}
          (wide-textfield {:label "E-Mail"
                           :type :email
                           :value email
                           :error (boolean email-error)
                           :helperText email-error
                           :inputProps {:aria-label "Email"
                                        :autoComplete :email}
                           :onChange (fn [e]
                                       (when email-error (m/set-value!! this :ui/email-error nil))
                                       (m/set-string!! this ::user/email :event e))}))
        (grid/item {:xs 12}
          (wide-textfield {:label "Password"
                           :type :password
                           :value password
                           :error (boolean password-error)
                           :helperText password-error
                           :inputProps {:aria-label "Password"
                                        :autoComplete :new-password}
                           :onChange (fn [e]
                                       (when password-error (m/set-value!! this :ui/password-error nil))
                                       (m/set-string!! this ::user/password :event e))}))
        (grid/item {:xs 12}
          (inputs/button {:variant :contained
                          :color :primary
                          :type :submit
                          :fullWidth true}
            "Sign up")))
      (layout/box {:mt 2 :clone true}
        (grid/grid
          {:container true
           :justify :flex-end}
          (grid/item {}
            (navigation/link
              (routing/with-route this (dr/path-to LoginPage)
                {:variant :body2})
              "Already have an account? Sign In")))))))


(defsc LoginPage [this {:keys [::user/email ::user/password]
                        :ui/keys [email-error password-error]}]
  {:query [::user/email
           :ui/email-error

           ::user/password
           :ui/password-error]
   :ident (fn [] [:SCREEN :sign-up-in])
   :route-segment ["login"]
   :initial-state {::user/email ""
                   :ui/email-error nil

                   ::user/password ""
                   :ui/password-error nil}}
  (paper-base
    (dom/form
      {:noValidate true
       :onSubmit (fn submit-login [e]
                   (evt/prevent-default! e)
                   (comp/transact! this [(sign-in #:user{::user/email email
                                                         ::user/password password})]))}
      (grid/container
        {:spacing 2
         :direction "column"}
        (grid/item {:xs 12}
          (dd/typography
            {:align "center"
             :variant "h5"}
            "Sign in"))
        (grid/item {:xs 12}
          (wide-textfield {:label "E-Mail"
                           :type :email
                           :error (boolean email-error)
                           :helperText email-error
                           :inputProps {:aria-label "E-Mail"
                                        :autoComplete :email}
                           :value email
                           :onChange (fn [e]
                                       (when email-error (m/set-value!! this :ui/email-error nil))
                                       (m/set-string!! this ::user/email :event e))}))
        (grid/item {:xs 12}
          (wide-textfield {:label "Password"
                           :type :password
                           :error (boolean password-error)
                           :helperText password-error
                           :inputProps {:aria-label "Password"
                                        :autoComplete :current-password}
                           :value password
                           :onChange (fn [e]
                                       (when password-error (m/set-value!! this :ui/password-error nil))
                                       (m/set-string!! this ::user/password :event e))}))
        (grid/item {:xs 12}
          (inputs/button {:variant :contained
                          :color :primary
                          :type :submit
                          :fullWidth true}
            "Sign in"))))
    (layout/box {:mt 2 :clone true}
      (grid/container
        {:justify :space-between}
        (grid/item {:xs true}
          (navigation/link {:variant :body2} "Forgot password?"))
        (grid/item {}
          (navigation/link
            (routing/with-route this (dr/path-to SignUpPage)
              {:variant :body2})
            "Don't have an account? Sign Up"))))))