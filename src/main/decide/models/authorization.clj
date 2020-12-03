(ns decide.models.authorization
  (:require
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]))

(defn check-logged-in [{::pc/keys [mutate] :as mutation}]
  (assoc mutation
    ::pc/mutate
    (fn [env params]
      (let [session (get-in env [:ring/request :session])]
        (if (:session/valid? session)
          (-> env (assoc :AUTH/user-id (:user/id session)) (mutate params))
          (throw (ex-info "User is not logged in!" {})))))))