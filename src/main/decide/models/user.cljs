(ns decide.models.user
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [goog.net.cookies]))

(def sign-in `sign-in)
(def sign-up `sign-up)

(defsc User [_ _]
  {:query [::id ::display-name]
   :ident ::id})

(defmutation sign-out [_]
  (action [{:keys [app state]}]
    (.clear goog.net.cookies)
    (swap! state assoc :root/current-session {:session/valid? false})
    (app/force-root-render! app))
  (remote [_] true))

(defmutation change-password [{:keys [old-password new-password]}]
  (ok-action [{:keys [component] :as env}]
    (let [{:keys [errors]}
          (get-in env [:result :body `change-password])]
      (if (empty? errors)
        (m/set-value!! component :ui/success-open? true)
        (cond
          (contains? errors :invalid-credentials)
          (m/set-string!! component :ui/old-password-error :value (i18n/tr "Password is wrong"))))))
  (remote [_env] true))