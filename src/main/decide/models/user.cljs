(ns decide.models.user
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]))

(def sign-in)
(def sign-up)

(defsc Session [_ _]
  {:query         [:session/valid? :user/id]
   :ident         (fn [] [:current-session])
   :initial-state {:session/valid? false
                   :user/id        nil}})


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