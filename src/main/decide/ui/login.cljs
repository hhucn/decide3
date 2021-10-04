(ns decide.ui.login
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [decide.models.authorization :as auth]
    [decide.models.user :as user]
    [mui.data-display :as dd]
    [mui.feedback.alert :as alert]
    [mui.feedback.dialog :as dialog]
    [mui.inputs :as inputs]
    [mui.layout.grid :as grid]
    [taoensso.timbre :as log]))

(defn reset-password-field! [component]
  (m/set-string! component ::user/password :value ""))

(def login-modal-ident [:dialog/id ::LoginDialog])

(defmutation show-signinup-dialog [{:keys [which-form] :or {which-form :sign-in}}]
  (action [{:keys [state]}]
    (swap! state update-in login-modal-ident
      assoc
      :ui/open? true
      :which-form which-form)))

(defmutation close-dialog [_]
  (action [{:keys [state]}]
    (swap! state update-in login-modal-ident assoc :ui/open? false)))

(defmutation sign-up [{::user/keys [_email _password]}]
  (action [_] true)
  (ok-action [{:keys [app component result]}]
    (let [{:keys [errors]} (get-in result [:body `user/sign-up])]
      (if (empty? errors)
        (do
          (reset-password-field! component)
          (comp/transact! component [(close-dialog {})])
          (app/force-root-render! app))
        (cond
          (contains? errors :email-in-use)
          (m/set-string! component :ui/nickname-error :value (i18n/tr "Nickname already in use"))))))
  (remote [env]
    (-> env
      (m/with-server-side-mutation `user/sign-up)
      (m/returning auth/Session)
      (m/with-target [:root/current-session]))))

(defmutation sign-in [{::user/keys [_email _password]}]
  (action [_] true)
  (ok-action [{:keys [app component] :as env}]
    (let [{:keys [errors]}
          (get-in env [:result :body `user/sign-in])]
      (if (empty? errors)
        (do
          (reset-password-field! component)
          (comp/transact! component [(close-dialog {})])
          (app/force-root-render! app))
        (when errors
          (cond
            (or
              (contains? errors :account-does-not-exist)
              (contains? errors :invalid-credentials))
            (m/set-string!! component :ui/password-error :value (i18n/tr "Nickname or password wrong")))))))
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
      {:fullWidth true}
      props)))

(defsc SignUpForm [this {:keys [:user/nickname ::user/password]
                         :ui/keys [nickname-error password-error]}]
  {:query [:user/nickname ::user/password
           :ui/nickname-error :ui/password-error]
   :ident (fn [] [:component/id ::SignInForm])
   :initial-state
   (fn [_] {:user/nickname ""
            :ui/nickname-error nil

            ::user/password ""
            :ui/password-error nil})}

  (dom/form
    {:name "signup"
     :onSubmit
     (fn submit-sign-up [e]
       (evt/prevent-default! e)
       (comp/transact! this [(sign-up {::user/email nickname ::user/password password})]))}
    (dialog/title {:sx {:textAlign :center}}
      (i18n/trc "Dialog header" "Sign up"))
    (dialog/content {}
      (grid/container {:spacing 2, :pt 1}
        (grid/item {:xs 12}
          (inputs/textfield
            {:label (i18n/tr "Nickname")
             :value nickname
             :name :username
             :required true
             :fullWidth true
             :error (boolean nickname-error)
             :helperText nickname-error
             :autoFocus true
             :inputProps {:aria-label (i18n/tr "Nickname")
                          :autoComplete :username
                          :minLength 4 :maxLength 15
                          :required true}
             :onChange (fn [e]
                         (when nickname-error (m/set-value!! this :ui/nickname-error nil))
                         (m/set-string!! this :user/nickname :event e))})

          (grid/item {:xs 12}
            (inputs/textfield
              {:label (i18n/tr "Password")
               :fullWidth true
               :type :password
               :required true
               :value password
               :error (boolean password-error)
               :helperText password-error
               :inputProps {:aria-label (i18n/tr "Password")
                            :autoComplete :new-password
                            :required true}
               :onChange (fn [e]
                           (when password-error (m/set-value!! this :ui/password-error nil))
                           (m/set-string!! this ::user/password :event e))}))

         (grid/item {:xs 12}
           (inputs/button {:variant :contained
                           :color :primary
                           :type :submit
                           :fullWidth true}
             (i18n/trc "Label of submit form" "Sign up"))
          (grid/container
            {:item true
             :justifyContent :flex-end}
            (grid/item {}
              (inputs/button
                {:color "inherit"
                 :onClick #(comp/transact! this [(show-signinup-dialog {:which-form :sign-in})])}
                (i18n/tr "Already have an account? Sign In"))))))))))

(def ui-signup-form (comp/computed-factory SignUpForm))

(defsc LoginForm [this {:keys [:user/nickname ::user/password]
                        :ui/keys [nickname-error password-error]}]
  {:query [:user/nickname
           :ui/nickname-error

           ::user/password
           :ui/password-error]
   :ident (fn [] [:component/id ::LoginForm])
   :initial-state
   (fn [_] {:user/nickname ""
            :ui/nickname-error nil

            ::user/password ""
            :ui/password-error nil})}
  (comp/fragment
    (dialog/title {:disableTypography true}
      (dd/typography {:align "center" :variant "h5" :component "h2"}
        (i18n/trc "Dialog header" "Sign in")))
    (dialog/content {}
      (alert/alert {:severity :info}
        (i18n/tr "Don't have an account?")
        (inputs/button {:color :inherit, :size :small
                        :onClick #(comp/transact! this [(show-signinup-dialog {:which-form :sign-up})])}
          (i18n/tr "Sign Up")))
      (grid/container
        {:spacing 1
         :component :form
         :noValidate true
         :onSubmit (fn submit-login [e]
                     (evt/prevent-default! e)
                     (comp/transact! this [(sign-in {::user/email nickname
                                                     ::user/password password})]))}
        (grid/item {:xs 12}
          (wide-textfield {:label (i18n/tr "Nickname")
                           :name :username
                           :error (boolean nickname-error)
                           :helperText nickname-error
                           :inputProps {:aria-label (i18n/tr "Nickname")
                                        :autoComplete :username
                                        :minLength 4 :maxLength 15
                                        :required true}
                           :value nickname
                           :autoFocus true
                           :onChange (fn [e]
                                       (when nickname-error (m/set-value! this :ui/nickname-error nil))
                                       (m/set-string!! this :user/nickname :event e))}))
        (grid/item {:xs 12}
          (wide-textfield {:label (i18n/tr "Password")
                           :type :password
                           :error (boolean password-error)
                           :helperText password-error
                           :inputProps {:aria-label (i18n/tr "Password")
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
            (i18n/trc "Label of submit form" "Sign in")))
        (grid/container
          {:item true
           :justifyContent :space-between}
          (grid/item {:xs true}
            #_(navigation/link {:variant :body2} "Forgot password?"))
          (grid/item {}
            (inputs/button
              {:color "inherit"
               :onClick #(comp/transact! this [(show-signinup-dialog {:which-form :sign-up})])}
              (i18n/tr "Don't have an account? Sign Up"))))))))

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
