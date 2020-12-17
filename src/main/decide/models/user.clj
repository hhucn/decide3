(ns decide.models.user
  (:require
    [buddy.hashers :as hs]
    [clojure.spec.alpha :as s]
    [com.fulcrologic.fulcro.components :refer [defsc]]
    [com.fulcrologic.fulcro.server.api-middleware :as fmw]
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [datahike.api :as d]
    [datahike.core :as d.core]
    [com.fulcrologic.guardrails.core :refer [>defn >defn- => | ? <-]]
    [taoensso.timbre :as log]))

(def schema [{:db/ident       :user/id
              :db/doc         "The id of a user"
              :db/unique      :db.unique/identity
              :db/cardinality :db.cardinality/one
              :db/valueType   :db.type/uuid}

             {:db/ident       :user/password
              :db/doc         "Password of a user"
              :db/cardinality :db.cardinality/one
              :db/valueType   :db.type/string
              :db/noHistory   true}

             {:db/ident       :user/email
              :db/unique      :db.unique/identity
              :db/cardinality :db.cardinality/one
              :db/valueType   :db.type/string}

             {:db/ident       ::display-name
              :db/doc         "A (not unique) name to display for the public."
              :db/cardinality :db.cardinality/one
              :db/valueType   :db.type/string}])

(s/def :user/id uuid?)
(s/def ::ident (s/tuple #{:user/id} :user/id))
(s/def :user/email string?)
(s/def :user/password string?)
(s/def ::display-name string?)

(defsc Session [_ _]
  {:query         [{::current-session [:session/valid? :user/id]}]
   :ident         (fn [] [:authorization :current-session])
   :initial-state {::current-session {:session/valid? false
                                      :user/id        nil}}})

(>defn hash-password [password]
  [:user/password => string?]
  (hs/derive password))

(>defn email-in-db?
  "True if email is already in the db."
  [db email]
  [d.core/db? :user/email => boolean?]
  (not (empty? (d/q '[:find ?e
                      :in $ ?email
                      :where [?e :user/email ?email]]
                 db email))))

(>defn get-by-email
  ([db email]
   [d.core/db? :user/email => map?]
   (get-by-email db email [:user/id :user/email]))
  ([db email query]
   [d.core/db? :user/email any? => map?]
   (d/pull db query [:user/email email])))

(>defn password-valid? [user attempt]
  [(s/keys :req [:user/password]) :user/password => boolean?]
  (let [{:user/keys [password]} user]
    (hs/check attempt password)))


(defn response-updating-session
  "Uses `mutation-response` as the actual return value for a mutation, but also stores the data into the (cookie-based) session."
  [mutation-env mutation-response upsert-session]
  (let [existing-session (some-> mutation-env :ring/request :session)]
    (fmw/augment-response
      mutation-response
      (fn [resp]
        (let [new-session (merge existing-session upsert-session)]
          (assoc resp :session new-session))))))


(defmutation sign-in [{:keys [db] :as env} {:user/keys [email password]}]
  {::pc/params [:user/email :user/password]
   ::pc/output [:session/valid? :user/id :signin/result :errors]}
  (if (email-in-db? db email)
    (let [{:user/keys [id] :as user} (get-by-email db email [:user/id :user/password])]
      (if (password-valid? user password)
        (response-updating-session env
          {:signin/result  :success
           :session/valid? true
           :user/id        id}
          {:user/id        id
           :session/valid? true})
        {:signin/result :fail
         :signin/errors #{:invalid-credentials}}))
    {:signin/result :fail
     :errors #{:account-does-not-exist}}))

;; API
(defmutation sign-up [{:keys [conn] :as env} {:user/keys [email password]}]
  {::pc/params [:user/email :user/password]
   ::pc/output [:session/valid? :user/id :signup/result]}
  (if (email-in-db? @conn email)
    {:errors #{:email-in-use}}
    (let [id (d.core/squuid)
          user #:user{:id            id
                      :email         email
                      ::display-name email
                      :password      (hash-password password)}
          tx-report (d/transact conn [user])]
      (response-updating-session env
        {:signup/result  :success
         :user/id        id
         :session/valid? true
         ::p/env         (assoc env :db (:db-after tx-report))}
        {:user/id        id
         :session/valid? true}))))

(defmutation sign-out [env _]
  {::pc/output [:session/valid?]}
  (response-updating-session env
    {:session/valid? false}
    {:session/valid? false :user/id nil}))

(defresolver current-session-resolver [env _]
  {::pc/output [{::current-session [:session/valid? :user/id]}]}
  (let [{:keys [session/valid? user/id]} (get-in env [:ring/request :session])]
    (if valid?
      {::current-session {:session/valid? true :user/id id}}
      {::current-session {:session/valid? false}})))

(defmutation change-password [{:keys [db conn AUTH/user-id]} {:keys [old-password new-password]}]
  {::pc/params [:old-password :new-password]
   ::pc/output [:errors]}
  (let [pw-valid? (-> db
                    (d/pull [::password] [:user/id user-id])
                    (password-valid? old-password))]
    (if pw-valid?
      (do (d/transact! conn [{:db/id     [:user/id user-id]
                              ::password new-password}])
          {})
      {:errors #{:invalid-credentials}})))

(defresolver resolve-public-infos [{:keys [db]} {:user/keys [id]}]
  {::pc/input  #{:user/id}
   ::pc/output [::display-name]}
  (d/pull db [::display-name] [:user/id id]))


(def resolvers [sign-up sign-in current-session-resolver sign-out change-password resolve-public-infos])