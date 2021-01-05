(ns decide.models.authorization
  (:require
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]))

(defn check-logged-in [{::pc/keys [mutate] :as mutation}]
  (assoc mutation
    ::pc/mutate
    (fn [env params]
      (if-let [user-id (get-in env [:ring/request :session])]
        (-> env (assoc :AUTH/user-id user-id) (mutate params))
        (throw (ex-info "User is not logged in!" {}))))))