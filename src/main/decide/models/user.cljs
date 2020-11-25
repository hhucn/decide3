(ns decide.models.user
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [decide.routing :as routing]
    [decide.ui.main-app :as todo-app]
    [decide.ui.proposal.main-proposal-list :as main-proposal-list]))

(defsc Session [_ _]
  {:query         [:session/valid? :user/id]
   :ident         (fn [] [:component/id :session])
   :initial-state {:session/valid?         false
                   :user/id nil}})

(defmutation sign-up [{:user/keys [_email _password]}]
  (action [_] true)
  (ok-action [{:keys [component] :as env}]
    (let [{:keys [errors]} (get-in env [:result :body `sign-up])]
      (if (empty? errors)
        (do
          (m/set-string! component :user/password :value "")
          (comp/transact! component [(routing/route-to {:path (dr/path-to todo-app/MainApp main-proposal-list/MainProposalList)})]))
        (cond
          (contains? errors :email-in-use)
          (m/set-string! component :ui/email-error :value "E-Mail already in use!")))))

  (remote [env] (m/returning env Session)))

(defmutation sign-in [{:user/keys [_email _password]}]
  (action [_] true)
  (ok-action [{:keys [component] :as env}]
    (let [{:keys [errors]}
          (get-in env [:result :body `sign-in])]
      (if (empty? errors)
        (do
          (m/set-string! component :user/password :value "")
          (comp/transact! component [(routing/route-to {:path (dr/path-to todo-app/MainApp main-proposal-list/MainProposalList)})]))
        (when errors
          (or
            (contains? errors :account-does-not-exist)
            (contains? errors :invalid-credentials))
          (m/set-string!! component :ui/password-error :value "E-Mail or password is wrong.")))))
  (remote [env] (m/returning env Session)))

(defmutation change-password [{:keys [old-password new-password]}]
  (ok-action [{:keys [component] :as env}]
    (let [{:keys [errors]}
          (get-in env [:result :body `change-password])]
      (if (empty? errors)
        (m/set-value!! component :ui/success-open? true)
        (cond
          (contains? errors :invalid-credentials)
          (m/set-string!! component :ui/old-password-error :value "Password is wrong.")))))
  (remote [_env] true))