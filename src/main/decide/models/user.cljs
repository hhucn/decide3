(ns decide.models.user
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]))

(def sign-in `sign-in)
(def sign-up `sign-up)

(defsc User [_ _]
  {:query [::id ::display-name]
   :ident ::id})

(defsc Session [_ _]
  {:query [:session/valid?
           {:user (comp/get-query User)}
           ::id]
   :initial-state {:session/valid? false}})


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