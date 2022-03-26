(ns decide.models.process.mutations
  (:require
    [clojure.spec.alpha :as s]
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [datahike.api :as d]
    [decide.models.argumentation.database :as argumentation.db]
    [decide.models.authorization :as auth]
    [decide.models.process :as process]
    [decide.models.process.database :as process.db]
    [decide.models.proposal :as-alias proposal]
    [decide.models.proposal.core :as proposal.core]
    [decide.models.proposal.database :as proposal.db]
    [decide.models.user :as user]
    [decide.models.user.database :as user.db]
    [decide.server-components.database :as db]))

(defn check-slug-exists [{::pc/keys [mutate] :as mutation}]
  (assoc mutation
    ::pc/mutate
    (fn [{:keys [db] :as env} {::process/keys [slug] :as params}]
      (if (process.db/slug-in-use? db slug)
        (mutate (assoc env :process (d/entity db [::process/slug slug])) params)
        (throw (ex-info "Slug is not in use!" {:slug slug}))))))

(defn needs-moderator [{::pc/keys [mutate] :as mutation}]
  (assoc mutation
    ::pc/mutate
    (fn [{:keys [db AUTH/user-id] :as env} {::process/keys [slug] :as params}]
      (let [process (d/pull db [{::process/moderators [::user/id]}] [::process/slug slug])]
        (if (process/moderator? process {::user/id user-id})
          (mutate env params)
          (throw (ex-info "Need moderation role for this operation" {::process/slug slug
                                                                     ::user/id user-id})))))))

(defn process-still-going [{::pc/keys [mutate] :as mutation}]
  (assoc mutation
    ::pc/mutate
    (fn [{:keys [db] :as env} {::process/keys [slug] :as params}]
      (let [process (d/pull db [::process/end-time] [::process/slug slug])]
        (if-not (process/over? process)
          (mutate env params)
          (throw (ex-info "The process is already over" {::process/slug slug})))))))


(defmutation add-process [{:keys [conn db AUTH/user] :as env}
                          {::process/keys [slug] :keys [participant-emails] :as process}]
  {::pc/params [::process/title ::process/slug ::process/description ::process/end-time ::process/type :process/features
                :participant-emails]
   ::pc/output [::process/slug]
   ::s/params (s/keys
                :req [::process/slug ::process/title ::process/description]
                :opt [::process/end-time ::process/type])
   ::pc/transform auth/check-logged-in}
  (let [existing-emails (filter #(user/email-in-db? db %) participant-emails)
        {:keys [db-after]}
        (db/transact-as conn user
          {:tx-data
           (process.db/->add db
             (-> process
               (update ::process/moderators conj user)      ; remove that here? let the user come through parameters?
               (update ::process/participants concat (map #(vector ::user/email %) existing-emails))))})]
    {::process/slug slug
     ::p/env (assoc env :db db-after)}))

;; TODO Fix this up... Proper validation and everything.
(defmutation update-process [{:keys [conn db AUTH/user] :as env} {::process/keys [slug] :as process}]
  {::pc/params [::process/slug ::process/title ::process/description ::process/start-time ::process/end-time ::process/type]
   ::pc/output [::process/slug]
   ::s/params
   (s/keys
     :req [::process/slug]
     :opt [::process/title ::process/description ::process/start-time ::process/end-time ::process/type])
   ::pc/transform (comp auth/check-logged-in check-slug-exists needs-moderator)}
  (if-let [existing-process (process.db/get-by-slug db slug [:db/id :process/features])]
    (let [{:keys [db-after]} (db/transact-as conn user {:tx-data (process.db/->update existing-process process)})]
      {::process/slug slug
       ::p/env (assoc env :db db-after)})
    (throw (ex-info "Slug is not in use!" {:slug slug}))))

(defmutation add-moderator [{:keys [conn db AUTH/user-id] :as env} {::process/keys [slug] email ::user/email}]
  {::pc/output [::user/id]
   ::s/params (s/keys :req [::process/slug ::user/email])
   ::pc/transform (comp auth/check-logged-in check-slug-exists needs-moderator)}
  (if-let [{::user/keys [id]} (and (user/email-in-db? db email) (user/get-by-email db email))]
    (let [{:keys [db-after]} (process.db/add-moderator! conn [::process/slug slug] user-id [::user/id id])]
      {::user/id id
       ::p/env (assoc env :db db-after)})
    (throw (ex-info "User with this email doesn't exist!" {:email email}))))

(defmutation add-participant [{:keys [conn db] :as env}
                              {user-id ::user/id slug ::process/slug}]
  {::pc/params [::process/slug ::user/id]
   ::pc/output [::process/slug]
   ::s/params (s/keys :req [::process/slug])
   ::pc/transform (comp auth/check-logged-in check-slug-exists needs-moderator)}
  (when-let [user (user.db/entity db user-id)]
    (let [process (d/entity db [::process/slug slug])
          {:keys [db-after]}
          (d/transact conn
            {:tx-data
             (concat
               (process.db/->enter process user)
               [[:db/add "datomic.tx" :tx/by [::user/id user-id]]])})]
      {::process/slug slug
       ::p/env (assoc env :db db-after)})))

(defmutation remove-participant [{:keys [conn db] :as env}
                                 {user-id ::user/id slug ::process/slug}]
  {::pc/params [::process/slug ::user/id]
   ::pc/output [::process/slug]
   ::s/params (s/keys :req [::process/slug])
   ::pc/transform (comp auth/check-logged-in check-slug-exists needs-moderator)}
  (when-let [user (user.db/entity db user-id)]
    (let [process (d/entity db [::process/slug slug])
          {:keys [db-after]}
          (d/transact conn
            {:tx-data
             (concat
               (process.db/->remove-participant process user)
               [[:db/add "datomic.tx" :tx/by [::user/id user-id]]])})]
      {::process/slug slug
       ::p/env (assoc env :db db-after)})))


(defmutation add-proposal [{:keys [conn db AUTH/user-id] :as env}
                           {::proposal/keys [id title body parents arguments]
                            ::process/keys [slug]
                            :or {parents [] arguments []}}]
  {::pc/params [::process/slug
                ::proposal/id ::proposal/title ::proposal/body ::proposal/parents ::proposal/arguments]
   ::pc/output [::proposal/id]
   ::pc/transform (comp auth/check-logged-in check-slug-exists)}
  (let [tempid id
        process (d/entity db [::process/slug slug])
        user (d/entity db [::user/id user-id])]

    (cond
      (not-every? #(proposal.db/exists? db %) (map ::proposal/id parents))
      (throw (ex-info "Not every parent exists" {}))

      (not-every? #(argumentation.db/exists? db %) (map ::proposal/id arguments))
      (throw (ex-info "Not every argument exists" {})))

    (let [parents (map #(d/entity db (find % ::proposal/id)) parents) ; get :db/ids
          arguments (map #(d/entity db (find % :argument/id)) arguments)
          new-proposal (assoc (proposal.db/new-base {:title title :body body})
                         :db/id "new-proposal"              ; tempid
                         ::proposal/original-author (:db/id user)
                         ::proposal/parents parents
                         ::proposal/arguments arguments)
          real-id (::proposal/id new-proposal)

          tx-report
          (proposal.core/add! conn user process new-proposal)]

      {::proposal/id real-id
       :tempids {tempid real-id}
       ::p/env (assoc env :db (:db-after tx-report))})))


(def all-mutations
  [add-process
   update-process

   add-moderator

   add-participant
   remove-participant

   add-proposal])