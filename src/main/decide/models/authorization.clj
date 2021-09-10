(ns decide.models.authorization
  (:require
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.guardrails.core :refer [>defn >defn- => | ? <-]]
    [com.wsscode.pathom.connect :as pc :refer [defmutation defresolver]]
    [datahike.api :as d]
    [decide.models.user :as user]))

(comp/defsc Session [_ _]
  {:query [{::current-session [:session/valid? ::user/id]}]
   :ident (fn [] [:authorization :current-session])
   :initial-state {::current-session {:session/valid? false
                                      ::user/id nil}}})

(>defn get-session-user-id [request]
  [map? => (? ::user/id)]
  (get-in request [:session :id]))

(defn get-session-user [db request]
  (when-let [id (get-session-user-id request)]
    (d/entity db [::user/id id])))

(defn- add-user-to-env [env user]
  (assoc env
    :AUTH/user user
    :AUTH/user-id (::user/id user)
    :session (when user
               {:user {:session/valid? true
                       ::user/id (::user/id user)}})))

(defn session-wrapper [env]
  (let [user (get-session-user (:db env) (:ring/request env))]
    (assoc env
      :AUTH/user user
      :AUTH/user-id (::user/id user)
      :session (when user
                 {:user {:session/valid? true
                         ::user/id (::user/id user)}}))))

(defn check-logged-in [{::pc/keys [mutate] :as mutation}]
  (assoc mutation
    ::pc/mutate
    (fn [env params]
      (if-let [user (get-session-user (:db env) (:ring/request env))]
        (mutate (add-user-to-env env user) params)
        (throw (ex-info "User is not logged in!" {}))))))

