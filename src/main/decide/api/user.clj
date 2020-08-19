(ns decide.api.user
  (:require
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [com.fulcrologic.fulcro.server.api-middleware :as fmw]
    [decide.models.user :as user]
    [taoensso.timbre :as log]
    [datahike.api :as d]
    [datahike.core :as d.core]))


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
   ::pc/output [:session/valid? :profile/nickname :signin/result :errors]}
  (if (user/email-in-db? db email)
    (let [{:keys [profile/_identities] :as user} (user/get-by-email db email [:user/id :user/password {:profile/_identities [:profile/nickname]}])
          nickname (:profile/nickname (first _identities))]
      (if (user/password-valid? user password)
        (response-updating-session env
          {:signin/result    :success
           :session/valid?   true
           :profile/nickname nickname}
          {:profile/nickname nickname
           :session/valid?   true})
        {:signin/result :fail
         :signin/errors #{:invalid-credentials}}))
    {:signin/result :fail
     :errors #{:account-does-not-exist}}))


(defmutation sign-up-user [{:keys [conn] :as env} {:user/keys [email password]}]
  {::pc/params [:user/email :user/password]
   ::pc/output [:session/valid? :profile/nickname :signup/result :errors]}
  (if (user/email-in-db? @conn email)
    {:signup/result :fail
     :errors        #{:email-in-use}}
    (let [id (d.core/squuid)
          user #:user{:id       id
                      :email    email
                      :password (user/hash-password password)}
          profile #:profile{:nickname   email
                            :name       email
                            :identities [[:user/id id]]}
          tx-report (d/transact conn [user profile])]
      (response-updating-session env
        {:signup/result    :success
         :profile/nickname email
         :session/valid?   true
         ::p/env           (assoc env :db (:db-after tx-report))}
        {:profile/nickname email
         :session/valid?   true}))))

(defmutation sign-out [env _]
  {::pc/output [:session/valid?]}
  (response-updating-session env
    {:session/valid? false}
    {:session/valid? false :profile/nickname nil}))

(defresolver current-session-resolver [env _]
  {::pc/output [{::current-session [:session/valid? :profile/nickname]}]}
  (let [{:keys [session/valid? profile/nickname] :as session} (get-in env [:ring/request :session])]
    (log/debug "Resolve Session for: " session)
    (if valid?
      {::current-session {:session/valid? true :profile/nickname nickname}}
      {::current-session {:session/valid? false}})))

(defmutation change-password [{:keys [db conn AUTH/user-id]} {:keys [old-password new-password]}]
  {::pc/params [:old-password :new-password]
   ::pc/output [:errors]}
  (let [pw-valid? (-> db
                    (d/pull [::user/password] [:user/id user-id])
                    (user/password-valid? old-password))]
    (if pw-valid?
      (do (d/transact! conn [{:db/id          [:user/id user-id]
                              ::user/password new-password}])
          {})
      {:errors #{:invalid-credentials}})))


(def resolvers [sign-up-user sign-in current-session-resolver sign-out change-password])