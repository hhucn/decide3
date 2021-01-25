(ns decide.models.authorization
  (:require
    [clojure.spec.alpha :as s]
    [com.fulcrologic.fulcro.components :as comp]
    [com.wsscode.pathom.connect :as pc :refer [defmutation defresolver]]
    [decide.models.user :as user]))

(s/def ::session (s/nilable ::user/id))

(comp/defsc Session [_ _]
  {:query [{::current-session [:session/valid? ::id]}]
   :ident (fn [] [:authorization :current-session])
   :initial-state {::current-session {:session/valid? false
                                      ::id nil}}})


(defn session-wrapper [env]
  (let [user-id (user/get-session-user-id env)]
    (assoc env
      :AUTH/user-id user-id
      :session (when user-id
                 {:user {:session/valid? true
                         ::user/id user-id}}))))

(defn check-logged-in [{::pc/keys [mutate] :as mutation}]
  (assoc mutation
    ::pc/mutate
    (fn [env params]
      (if-let [session (user/get-session-user-id env)]
        (mutate env params)
        #_(-> env (assoc :session session) (mutate params)) ; instead of session-wrapper?
        (throw (ex-info "User is not logged in!" {}))))))

