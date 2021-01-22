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

(>defn get-session [env]
  [map? => ::session]
  (get-in env [:ring/request :session]))

(defn session-wrapper [env]
  (assoc env
    :AUTH/user-id (get-session env)
    :session (get-session env)))

(defn check-logged-in [{::pc/keys [mutate] :as mutation}]
  (assoc mutation
    ::pc/mutate
    (fn [env params]
      (if-let [session (get-session env)]
        (-> env (assoc :session session) (mutate params))
        (throw (ex-info "User is not logged in!" {}))))))

(>defn response-updating-session
  "Uses `mutation-response` as the actual return value for a mutation, but also stores the data into the (cookie-based) session."
  [mutation-response upsert-session]
  [any? ::session => any?]
  (fmw/augment-response mutation-response
    #(assoc % :session upsert-session)))

(>defn wrap-session [{:keys [decide.models.user/id] :as response}]
  [(s/keys :req [:decide.models.user/id]) => (s/keys :req [:decide.models.user/id])]
  (response-updating-session response id))


(defresolver current-session-resolver [env _]
  {::pc/output [{::current-session
                 [:session/valid?
                  {:user [:decide.models.user/id]}
                  :decide.models.user/id]}]}
  {::current-session
   (let [{:keys [decide.models.user/id]} (get-in env [:ring/request :session])]
     (if id
       {:session/valid? true
        :user {:decide.models.user/id id}
        :decide.models.user/id id}
       {:session/valid? false}))})

(def resolver [current-session-resolver])