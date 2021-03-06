(ns decide.models.user
  (:require
   [clojure.spec.alpha :as s]
   [com.fulcrologic.fulcro.server.api-middleware :as fmw]
   [com.fulcrologic.guardrails.core :refer [=> >defn]]
   [com.wsscode.pathom.connect :as pc :refer [defmutation defresolver]]
   [com.wsscode.pathom.core :as p]
   [datahike.api :as d]
   [datahike.core :as d.core]
   [decide.user :as user]))


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

   {:db/ident :tx/by
    :db/doc "Ref tu user who authored the transaction. Useful for audits."
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}

   {:db/ident :user/language
    :db/doc "The preferred language for the user. ISO 639"
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one}])

(s/def ::id ::user/id)
(s/def ::ident (s/tuple #{::id} ::id))
(s/def ::lookup (s/or :ident ::ident :db/id pos-int?))
(s/def ::email string?)
(s/def :user/email ::user/email)
(s/def ::password (s/or :plain ::user/plain-password
                    :encrypted ::user/encrypted-password))
(s/def ::display-name ::user/display-name)
(s/def :user/language ::user/language)

(s/def ::entity (s/and associative? #(contains? % :db/id)))

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
     ::password (user/hash-password password)}))


(defmutation sign-in [{:keys [db] :as env} {::keys [email password]}]
  {::pc/params [::email ::password]
   ::pc/output [:session/valid? ::id {:user [::id]}
                :signin/result :errors]}
  (if (email-in-db? db email)
    (let [{::keys [id] :as user} (get-by-email db email [::id ::password])]
      (if (user/password-valid? (::password user) password)
        (wrap-session env
          {:signin/result :success
           :session/valid? true
           :user {::id id}
           ::id id})
        {:signin/result :fail
         :errors #{:invalid-credentials}}))

    ;; TODO register for now if not in DB...revert this later
    #_(let [{::keys [id] :as user} (tx-map {::email email ::password password})
            tx-report (d/transact conn [user [:db/add "datomic.tx" :tx/by [::id id]]])]
        (wrap-session env
          {:signup/result :success
           ::id id
           :user {::id id}
           :session/valid? true
           ::p/env (assoc env :db (:db-after tx-report))}))
    {:signin/result :fail
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
          tx-report (d/transact conn [user [:db/add "datomic.tx" :tx/by [::id id]]])]
      (wrap-session env
        {:signup/result :success
         ::id id
         :user {::id id}
         :session/valid? true
         ::p/env (assoc env :db (:db-after tx-report))}))))

(defmutation sign-out [env _]
  {::pc/output [:session/valid?]}
  (wrap-session env {:session/valid? false ::id nil}))

(defmutation change-password [{:keys [conn AUTH/user]} {:keys [old-password new-password]}]
  {::pc/params [:old-password :new-password]
   ::pc/output [:errors]}
  (if (user/password-valid? (::password user) old-password)
    (do (d/transact conn
          {:tx-data
           [{:db/id (:db/id user)
            ::password (user/hash-password new-password)}
            [:db/add "datomic.tx" :tx/by (:db/id user)]]})
        {})
    {:errors #{:invalid-credentials}}))

(defresolver current-session-resolver [{:keys [AUTH/user] :as env} _]
  {::pc/output [{::current-session
                 [:session/valid?
                  {:user [::id ::display-name]}
                  ::id]}]}
  {::current-session
   (wrap-session env
     (if user
       {:session/valid? true
        :user (select-keys user [::id ::display-name])
        ::id (::id user)}
       {:session/valid? false
        ::id nil}))})


(def resolvers [sign-up sign-in sign-out change-password current-session-resolver])
