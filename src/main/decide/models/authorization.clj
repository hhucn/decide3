(ns decide.models.authorization
  (:require
    [clojure.spec.alpha :as s]
    [com.fulcrologic.fulcro.components :as comp]
    [com.wsscode.pathom.connect :as pc :refer [defmutation defresolver]]
    [decide.models.user :as user]
    [decide.models.user.database :as user.db]))

(s/def ::session (s/nilable ::user/id))

(comp/defsc Session [_ _]
  {:query [{::current-session [:session/valid? ::id]}]
   :ident (fn [] [:authorization :current-session])
   :initial-state {::current-session {:session/valid? false
                                      ::id nil}}})

(defn- add-user-id-to-env [env user-id]
  (assoc env
    :AUTH/user (user.db/get-entity (:db env) [::user/id user-id])
    :AUTH/user-id user-id
    :session (when user-id
               {:user {:session/valid? true
                       ::user/id user-id}})))

(defn session-wrapper [env]
  (let [user-id (user/get-session-user-id (:ring/request env))]
    (add-user-id-to-env env user-id)))

(defn check-logged-in [{::pc/keys [mutate] :as mutation}]
  (assoc mutation
    ::pc/mutate
    (fn [env params]
      (if-let [user-id (user/get-session-user-id (:ring/request env))]
        (-> env (add-user-id-to-env user-id) (mutate params)) ; instead of session-wrapper?
        (throw (ex-info "User is not logged in!" {}))))))

