(ns decide.models.user
  (:require
    [buddy.hashers :as hs]
    [clojure.spec.alpha :as s]
    [com.fulcrologic.fulcro.server.api-middleware :as fmw]
    [com.fulcrologic.guardrails.core :refer [>defn >defn- => | ? <-]]
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [datahike.api :as d]
    [datahike.core :as d.core]))


(def schema
  [{:db/ident ::id
    :db/doc "The id of a user"
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one
    :db/valueType :db.type/uuid}

   {:db/ident ::password
    :db/doc "Password of a user"
    :db/cardinality :db.cardinality/one
    :db/valueType :db.type/string
    :db/noHistory true}

   {:db/ident ::email
    :db/doc "DEPRECATED - Symbolic unique identifier for a user."
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one
    :db/valueType :db.type/string}
   #_[:db/add ::email :db/ident :user/nickname]             ; This doesn't work.. Don't know if it's a datahike issue

   {:db/ident :user/email
    :db/doc "User email for contact."
    :db/cardinality :db.cardinality/one
    :db/valueType :db.type/string
    #_#_:db/noHistory true}                                 ; no need to keep privacy data

   {:db/ident ::display-name
    :db/doc "A (not unique) name to display for the public."
    :db/cardinality :db.cardinality/one
    :db/valueType :db.type/string}

   {:db/ident ::roles
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/many}

   {:db/ident :db/txUser
    :db/doc "Ref tu user who authored the transaction. Useful for audits."
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}

   {:db/ident :user/language
    :db/doc "The preferred language for the user. ISO 639"
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one}])

(s/def ::id uuid?)
(s/def ::ident (s/tuple #{::id} ::id))
(s/def ::lookup (s/or :ident ::ident :db/id pos-int?))
(s/def ::email string?)
(s/def :user/email string?)
(s/def ::encrypted-password string?)
(s/def ::password (s/or :encrypted ::encrypted-password :raw string?))
(s/def ::display-name (s/and string? #(< 0 (count %) 50)))
(s/def :iso/ISO-639-3 #(>= 3 (count (name %))))
(s/def :user/language (s/and simple-keyword? :iso/ISO-639-3))

(s/def ::entity (s/and associative? #(contains? % :db/id)))

(>defn hash-password [password]
  [::password => ::encrypted-password]
  (hs/derive password))

(>defn exists? [db user-id]
  [d.core/db? ::id => boolean?]
  (some? (d/q '[:find ?e . :in $ ?user-id :where [?e ::id ?user-id]] db user-id)))

(>defn email-in-db?
  "True if email is already in the db."
  [db email]
  [d.core/db? ::email => boolean?]
  (not (empty? (d/q '[:find ?e
                      :in $ ?email
                      :where [?e ::email ?email]]
                 db email))))

(>defn get-by-email
  ([db email]
   [d.core/db? ::email => map?]
   (get-by-email db email [::id ::email]))
  ([db email query]
   [d.core/db? ::email vector? => map?]
   (d/pull db query [::email email])))

(>defn password-valid? [{:keys [::password]} attempt]
  [(s/keys :req [::password]) ::password => boolean?]
  (hs/check attempt password))

(>defn response-updating-session
  "Uses `mutation-response` as the actual return value for a mutation, but also stores the data into the (cookie-based) session."
  [mutation-env mutation-response upsert-session]
  [any? any? map? => any?]
  (let [existing-session (some-> mutation-env :ring/request :session)]
    (fmw/augment-response
      mutation-response
      (fn [resp]
        (let [new-session (merge existing-session upsert-session)]
          (assoc resp :session new-session))))))


(defn wrap-session [env {:keys [decide.models.user/id] :as response}]
  (response-updating-session env response {:id id}))


(>defn tx-map [{::keys [id email password display-name]}]
  [(s/keys :req [::email ::password] :opt [::id ::display-name])
   => (s/keys :req [::id ::display-name ::email ::password])]
  (let [id (or id (d.core/squuid))
        display-name (or display-name email)]
    {::id id
     ::email email
     ::display-name display-name
     ::password (hash-password password)}))


(defmutation sign-in [{:keys [conn db] :as env} {::keys [email password]}]
  {::pc/params [::email ::password]
   ::pc/output [:session/valid? ::id {:user [::id]}
                :signin/result :errors]}
  (if (email-in-db? db email)
    (let [{::keys [id] :as user} (get-by-email db email [::id ::password])]
      (if (password-valid? user password)
        (wrap-session env
          {:signin/result :success
           :session/valid? true
           :user {::id id}
           ::id id})
        {:signin/result :fail
         :errors #{:invalid-credentials}}))

    ;; TODO register for now if not in DB...revert this later
    (let [{::keys [id] :as user} (tx-map {::email email ::password password})
          tx-report (d/transact conn [user [:db/add "datomic.tx" :db/txUser [::id id]]])]
      (wrap-session env
        {:signup/result :success
         ::id id
         :user {::id id}
         :session/valid? true
         ::p/env (assoc env :db (:db-after tx-report))}))
    #_{:signin/result :fail
       :errors #{:account-does-not-exist}}))

;; API
(defmutation sign-up [{:keys [conn] :as env} {::keys [email password]}]
  {::pc/params [::email ::password]
   ::pc/output [:session/valid?
                {:user [::id]}
                ::id :signup/result]}
  (if (email-in-db? @conn email)
    {:errors #{:email-in-use}}
    (let [{::keys [id] :as user} (tx-map {::email email ::password password})
          tx-report (d/transact conn [user [:db/add "datomic.tx" :db/txUser [::id id]]])]
      (wrap-session env
        {:signup/result :success
         ::id id
         :user {::id id}
         :session/valid? true
         ::p/env (assoc env :db (:db-after tx-report))}))))

(defmutation sign-out [env _]
  {::pc/output [:session/valid?]}
  (wrap-session env {:session/valid? false ::id nil}))

(defmutation change-password [{:keys [db conn AUTH/user-id]} {:keys [old-password new-password]}]
  {::pc/params [:old-password :new-password]
   ::pc/output [:errors]}
  (let [pw-valid? (-> db
                    (d/pull [::password] [::id user-id])
                    (password-valid? old-password))]
    (if pw-valid?
      (do (d/transact! conn [{:db/id [::id user-id]
                              ::password new-password}
                             [:db/add "datomic.tx" :db/txUser [::id user-id]]])
          {})
      {:errors #{:invalid-credentials}})))

; TODO Move the following to different ns
(>defn get-session-user-id [request]
  [map? => (s/nilable ::id)]
  (get-in request [:session :id]))

(defn get-current-session [db request]
  (let [id (get-session-user-id request)]
    (if (and id (exists? db id))
      {:session/valid? true
       :user {::id id}
       ::id id}
      {:session/valid? false
       ::id nil})))

(defresolver current-session-resolver [{:keys [db ring/request] :as env} _]
  {::pc/output [{::current-session
                 [:session/valid?
                  {:user [::id]}
                  ::id]}]}
  {::current-session
   (wrap-session env
     (get-current-session db request))})


(def resolvers [sign-up sign-in sign-out change-password current-session-resolver])