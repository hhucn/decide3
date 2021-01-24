(ns decide.models.authorization
  (:require
    [clojure.spec.alpha :as s]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.server.api-middleware :as fmw]
    [com.fulcrologic.guardrails.core :refer [>defn => |]]
    [com.wsscode.pathom.connect :as pc :refer [defmutation defresolver]]))

(s/def ::session (s/nilable :decide.models.user/id))

(comp/defsc Session [_ _]
  {:query [{::current-session [:session/valid? ::id]}]
   :ident (fn [] [:authorization :current-session])
   :initial-state {::current-session {:session/valid? false
                                      ::id nil}}})

(>defn get-session-user-id [env]
  [map? => ::session]
  (get-in env [:ring/request :session :id]))

(defn session-wrapper [env]
  (let [user-id (get-session-user-id env)]
    (assoc env
      :AUTH/user-id user-id
      :session (when user-id
                 {:user {:session/valid? true
                         :decide.models.user/id user-id}}))))

(defn check-logged-in [{::pc/keys [mutate] :as mutation}]
  (assoc mutation
    ::pc/mutate
    (fn [env params]
      (if-let [session (get-session-user-id env)]
        (mutate env params)
        #_(-> env (assoc :session session) (mutate params)) ; instead of session-wrapper?
        (throw (ex-info "User is not logged in!" {}))))))

(>defn response-updating-session
  "Uses `mutation-response` as the actual return value for a mutation, but also stores the data into the (cookie-based) session."
  [mutation-env mutation-response upsert-session]
  [any? any? (s/keys :req-un [:decide.models.user/id]) => any?]
  (let [existing-session (some-> mutation-env :ring/request :session)]
    (fmw/augment-response
      mutation-response
      (fn [resp]
        (let [new-session (merge existing-session upsert-session)]
          (assoc resp :session new-session))))))


(>defn wrap-session [env {:keys [decide.models.user/id] :as response}]
  [map? (s/keys :opt [:decide.models.user/id]) => (s/keys :opt [:decide.models.user/id])]
  (response-updating-session env response {:id id}))


(defresolver current-session-resolver [env _]
  {::pc/output [{::current-session
                 [:session/valid?
                  {:user [:decide.models.user/id]}
                  :decide.models.user/id]}]}
  {::current-session
   (let [id (get-session-user-id env)]
     (if id
       {:session/valid? true
        :user {:decide.models.user/id id}
        :decide.models.user/id id}
       {:session/valid? false}))})

(def resolvers [current-session-resolver])