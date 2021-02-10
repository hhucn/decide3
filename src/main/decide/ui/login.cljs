(ns decide.ui.login
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]

    [decide.models.authorization :as auth]
    [decide.models.user :as user]

    [material-ui.data-display :as dd]
    [material-ui.feedback.dialog :as dialog]
    [material-ui.inputs :as inputs]
    [material-ui.layout.grid :as grid]))

(defn reset-password-field! [component]
  (m/set-string! component ::user/password :value ""))

(def login-modal-ident [:component/id ::LoginDialog])

(defmutation show-signinup-dialog [{:keys [which-form] :or {which-form :sign-in}}]
  (action [{:keys [state]}]
    (swap! state update-in login-modal-ident
      assoc
      :ui/open? true
      :which-form which-form)))

(defmutation close-dialog [_]
  (action [{:keys [state]}]
    (swap! state update-in login-modal-ident assoc :ui/open? false)))

(defmutation sign-up [{:user/keys [_email _password]}]
  (action [_] true)
  (ok-action [{:keys [component result]}]
    (let [{:keys [errors]} (get-in result [:body `user/sign-up])]
      (if (empty? errors)
        (do
          (reset-password-field! component)
          (comp/transact! component [(close-dialog {})]))
        (cond
          (contains? errors :email-in-use)
          (m/set-string! component :ui/email-error :value "Email already in use!")))))
  (remote [env]
    (-> env
      (m/with-server-side-mutation `user/sign-up)
      (m/returning auth/Session)
      (m/with-target [:root/current-session]))))

(defmutation sign-in [{:user/keys [_email _password]}]
  (action [_] true)
  (ok-action [{:keys [component] :as env}]
    (let [{:keys [errors]}
          (get-in env [:result :body `user/sign-in])]
      (if (empty? errors)
        (do
          (reset-password-field! component)
          (comp/transact! component [(close-dialog {})]))
        (when errors
          (cond
            (or
              (contains? errors :account-does-not-exist)
              (contains? errors :invalid-credentials))
            (m/set-string!! component :ui/password-error :value "Email or password is wrong."))))))
  (remote [env]
    (-> env
      (m/with-server-side-mutation `user/sign-in)
      (m/returning auth/Session)
      (m/with-target [:root/current-session]))))

(defn wide-textfield
  "Outlined textfield on full width with normal margins. Takes the same props as `material-ui.inputs/textfield`"
  [props]
  (inputs/textfield
    (merge
      {:variant :outlined
       :fullWidth true}
      props)))

(defsc SignUpForm [this {::user/keys [email password]
                         :ui/keys [email-error password-error]}]
  {:query [::user/email ::user/password
           :ui/email-error :ui/password-error]
   :ident (fn [] [:component/id ::SignInForm])
   :initial-state
   (fn [_] {::user/email ""
            :ui/email-error nil

            ::user/password ""
            :ui/password-error nil})}
  (comp/fragment
    (dialog/title {:disableTypography true}
      (dd/typography {:align "center" :variant "h5" :component "h2"}
        "Sign up"))
    (dialog/content {}
      (grid/container
        {:spacing 1
         :component :form
         :noValidate true
         :onSubmit (fn submit-sign-up [e]
                     (evt/prevent-default! e)
                     (comp/transact! this [(sign-up #:user{::user/email email ::user/password password})]))}
        (grid/item {:xs 12}
          (wide-textfield {:label "E-Mail"
                           :type :email
                           :value email
                           :error (boolean email-error)
                           :helperText email-error
                           :autoFocus true
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
            "Sign up"))
        (grid/container
          {:item true
           :justify :flex-end}
          (grid/item {}
            (inputs/button
              {:color "inherit"
               :onClick #(comp/transact! this [(show-signinup-dialog {:which-form :sign-in})])}
              "Already have an account? Sign In")))))))

(def ui-signup-form (comp/computed-factory SignUpForm))

(defsc LoginForm [this {:keys [::user/email ::user/password]
                        :ui/keys [email-error password-error]}]
  {:query [::user/email
           :ui/email-error

           ::user/password
           :ui/password-error]
   :ident (fn [] [:component/id ::LoginForm])
   :initial-state
   (fn [_] {::user/email ""
            :ui/email-error nil

            ::user/password ""
            :ui/password-error nil})}
  (comp/fragment
    (dialog/title {:disableTypography true}
      (dd/typography {:align "center" :variant "h5" :component "h2"}
        "Sign in"))
    (dialog/content {}
      (grid/container
        {:spacing 1
         :component :form
         :noValidate true
         :onSubmit (fn submit-login [e]
                     (evt/prevent-default! e)
                     (comp/transact! this [(sign-in #:user{::user/email email
                                                           ::user/password password})]))}
        (grid/item {:xs 12}
          (wide-textfield {:label "E-Mail"
                           :type :email
                           :error (boolean email-error)
                           :helperText email-error
                           :inputProps {:aria-label "E-Mail"
                                        :autoComplete :email}
                           :value email
                           :autoFocus true
                           :onChange (fn [e]
                                       (when email-error (m/set-value! this :ui/email-error nil))
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
                                       (when password-error (m/set-value! this :ui/password-error nil))
                                       (m/set-string!! this ::user/password :event e))}))
        (grid/item {:xs 12}
          (inputs/button {:variant :contained
                          :color :primary
                          :type :submit
                          :fullWidth true}
            "Sign in"))
        (grid/container
          {:item true
           :justify :space-between}
          (grid/item {:xs true}
            #_(navigation/link {:variant :body2} "Forgot password?"))
          (grid/item {}
            (inputs/button
              {:color "inherit"
               :onClick #(comp/transact! this [(show-signinup-dialog {:which-form :sign-up})])}
              "Don't have an account? Sign Up")))))))

(def ui-login-form (comp/computed-factory LoginForm))

(defsc LoginDialog [this {:keys [ui/open? ui/login-form ui/signup-form which-form]}]
  {:query [:ui/open?
           {:ui/login-form (comp/get-query LoginForm)}
           {:ui/signup-form (comp/get-query SignUpForm)}
           :which-form]
   :ident (fn [] login-modal-ident)
   :initial-state
   (fn [_]
     {:ui/open? false
      :which-form :sign-up
      :ui/login-form (comp/get-initial-state LoginForm)
      :ui/signup-form (comp/get-initial-state SignUpForm)})}
  (dialog/dialog
    {:open open?
     :maxWidth "md"
     :onClose #(m/set-value! this :ui/open? false)}
    (case which-form
      :sign-in (ui-login-form login-form)
      :sign-up (ui-signup-form signup-form)
      (str "This shouldn't happen: which-form:" which-form " Please report this to the administrator."))))

(def ui-login-modal (comp/computed-factory LoginDialog))
