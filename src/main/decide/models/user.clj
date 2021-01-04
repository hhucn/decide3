(ns decide.models.user
  (:require
    [buddy.hashers :as hs]
    [clojure.spec.alpha :as s]
    [com.fulcrologic.fulcro.components :refer [defsc]]
    [com.fulcrologic.fulcro.server.api-middleware :as fmw]
    [com.fulcrologic.guardrails.core :refer [>defn >defn- => | ? <-]]
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [datahike.api :as d]
    [datahike.core :as d.core]))

(def schema [{:db/ident ::id
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
              :db/unique :db.unique/identity
              :db/cardinality :db.cardinality/one
              :db/valueType :db.type/string}

             {:db/ident ::display-name
              :db/doc "A (not unique) name to display for the public."
              :db/cardinality :db.cardinality/one
              :db/valueType :db.type/string}])

(s/def ::id uuid?)
(s/def ::ident (s/tuple #{::id} ::id))
(s/def ::email string?)
(s/def ::password string?)
(s/def ::encrypted-password string?)
(s/def ::display-name string?)

(defsc Session [_ _]
  {:query [{::current-session [:session/valid? ::id]}]
   :ident (fn [] [:authorization :current-session])
   :initial-state {::current-session {:session/valid? false
                                      ::id nil}}})

(>defn hash-password [password]
  [::password => ::encrypted-password]
  (hs/derive password))

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
  [(s/keys :req [::encrypted-password]) ::password => boolean?]
  (hs/check attempt password))

(>defn tx-map [{::keys [id email password display-name]}]
  [(s/keys :req [::email ::password] :opt [::id ::display-name])
   => (s/keys :req [::id ::display-name ::email ::encrypted-password])]
  (let [id (or id (d.core/squuid))
        display-name (or display-name email)]
    {::id id
     ::email email
     ::display-name display-name
     ::password (hash-password password)}))

(defn response-updating-session
  "Uses `mutation-response` as the actual return value for a mutation, but also stores the data into the (cookie-based) session."
  [mutation-response upsert-session]
  (fmw/augment-response mutation-response
    #(assoc % :session upsert-session)))


(defmutation sign-in [{:keys [db]} {::keys [email password]}]
  {::pc/params [::email ::password]
   ::pc/output [:session/valid? ::id :signin/result :errors]}
  (if (email-in-db? db email)
    (let [{::keys [id] :as user} (get-by-email db email [::id ::password])]
      (if (password-valid? user password)
        (response-updating-session
          {:signin/result :success
           :session/valid? true
           ::id id}
          id)
        {:signin/result :fail
         :errors #{:invalid-credentials}}))
    {:signin/result :fail
     :errors #{:account-does-not-exist}}))

;; API
(defmutation sign-up [{:keys [conn] :as env} {::keys [email password]}]
  {::pc/params [::email ::password]
   ::pc/output [:session/valid? ::id :signup/result]}
  (if (email-in-db? @conn email)
    {:errors #{:email-in-use}}
    (let [{::keys [id] :as user} (tx-map {::email email ::password password})
          tx-report (d/transact conn [user])]
      (response-updating-session
        {:signup/result :success
         ::id id
         :session/valid? true
         ::p/env (assoc env :db (:db-after tx-report))}
        id))))

(defmutation sign-out []
  {::pc/output [:session/valid?]}
  (response-updating-session
    {:session/valid? false}
    nil))

(defresolver current-session-resolver [env]
  {::pc/output [{::current-session [:session/valid? ::id]}]}
  (let [{:keys [session/valid? decide.models.user/id]} (get-in env [:ring/request :session])]
    (if valid?
      {::current-session {:session/valid? true ::id id}}
      {::current-session {:session/valid? false}})))

(defmutation change-password [{:keys [db conn AUTH/user-id]} {:keys [old-password new-password]}]
  {::pc/params [:old-password :new-password]
   ::pc/output [:errors]}
  (let [pw-valid? (-> db
                    (d/pull [::password] [::id user-id])
                    (password-valid? old-password))]
    (if pw-valid?
      (do (d/transact! conn [{:db/id [::id user-id]
                              ::password new-password}])
          {})
      {:errors #{:invalid-credentials}})))

(defresolver resolve-public-infos [{:keys [db]} {::keys [id]}]
  {::pc/input #{::id}
   ::pc/output [::display-name]}
  (d/pull db [::display-name] [::id id]))


(def resolvers [sign-up sign-in current-session-resolver sign-out change-password resolve-public-infos])