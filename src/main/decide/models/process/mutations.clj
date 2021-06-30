(ns decide.models.process.mutations
  (:require
    [clojure.spec.alpha :as s]
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [datahike.api :as d]
    [decide.models.authorization :as auth]
    [decide.models.opinion :as opinion]
    [decide.models.process :as process]
    [decide.models.process.database :as process.db]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]))

(defn check-slug-exists [{::pc/keys [mutate] :as mutation}]
  (assoc mutation
    ::pc/mutate
    (fn [{:keys [db] :as env} {::process/keys [slug] :as params}]
      (if (process.db/slug-in-use? db slug)
        (mutate env params)
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


(defmutation add-process [{:keys [conn db AUTH/user-id] :as env}
                          {::process/keys [slug] :keys [participant-emails] :as process}]
  {::pc/params [::process/title ::process/slug ::process/description ::process/end-time ::process/type :process/features
                :participant-emails]
   ::pc/output [::process/slug]
   ::s/params (s/keys
                :req [::process/slug ::process/title ::process/description]
                :opt [::process/end-time ::process/type])
   ::pc/transform auth/check-logged-in}
  (let [user {::user/id user-id}
        existing-emails (filter #(user/email-in-db? db %) participant-emails)
        {:keys [db-after]}
        (d/transact conn
          {:tx-data
           (conj (process.db/->add db
                   (-> process
                     (update ::process/moderators conj user)  ; remove that here? let the user come through parameters?
                     (update ::process/participants concat (map #(vector ::user/email %) existing-emails))))
             [:db/add "datomic.tx" :db/txUser [::user/id user-id]])})]
    {::process/slug slug
     ::p/env (assoc env :db db-after)}))

;; TODO Fix this up... Propoer validation and everything.
(defmutation update-process [{:keys [conn db AUTH/user-id] :as env} {::process/keys [slug] :as process}]
  {::pc/params [::process/slug ::process/title ::process/description ::process/end-time ::process/type]
   ::pc/output [::process/slug]
   ; ::s/params (s/keys :req [::process/slug] :opt [::process/title ::process/description ::process/end-time ::process/type])
   ::pc/transform (comp auth/check-logged-in check-slug-exists needs-moderator)}
  (let [{:keys [db-after]}
        (d/transact conn
          {:tx-data
           (-> (process.db/->update db process) (conj [:db/add "datomic.tx" :db/txUser [::user/id user-id]]))})]
    {::process/slug slug
     ::p/env (assoc env :db db-after)}))

(defn add-moderator! [conn process-lookup moderator-id new-moderator-lookup]
  (d/transact conn
    [[:db/add process-lookup ::process/moderators new-moderator-lookup]
     [:db/add "datomic.tx" :db/txUser [::user/id moderator-id]]]))

(defmutation add-moderator [{:keys [conn db AUTH/user-id] :as env} {::process/keys [slug] email ::user/email}]
  {::pc/output [::user/id]
   ::s/params (s/keys :req [::process/slug ::user/email])
   ::pc/transform (comp auth/check-logged-in check-slug-exists needs-moderator)}
  (if-let [{::user/keys [id]} (and (user/email-in-db? db email) (user/get-by-email db email))]
    (let [{:keys [db-after]} (add-moderator! conn [::process/slug slug] user-id [::user/id id])]
      {::user/id id
       ::p/env (assoc env :db db-after)})
    (throw (ex-info "User with this email doesn't exist!" {:email email}))))

(defmutation add-participant [{:keys [conn db AUTH/user-id] :as env}
                              {user-emails :user-emails slug ::process/slug}]
  {::pc/params [::process/slug ::user/email]
   ::pc/output [::process/slug]
   ::s/params (s/keys :req [::process/slug])
   ::pc/transform (comp auth/check-logged-in check-slug-exists needs-moderator)}
  (let [user-lookups
        (for [user-email user-emails
              :when (user/email-in-db? db user-email)]
          [::user/email user-email])

        {:keys [db-after]}
        (d/transact conn
          (conj
            (process.db/->enter [::process/slug] user-lookups)
            [:db/add "datomic.tx" :db/txUser [::user/id user-id]]))]
    {::process/slug slug
     ::p/env (assoc env :db db-after)}))

(defmutation add-proposal [{:keys [conn AUTH/user-id] :as env}
                           {::proposal/keys [id title body parents arguments]
                            ::process/keys [slug]
                            :or {parents [] arguments []}}]
  {::pc/params [::process/slug
                ::proposal/id ::proposal/title ::proposal/body ::proposal/parents ::proposal/arguments]
   ::pc/output [::proposal/id]
   ::pc/transform (comp auth/check-logged-in check-slug-exists)}
  (let [process (d/pull @conn [::process/slug ::process/end-time] [::process/slug slug])]
    (when (process/over? process) (throw (ex-info "Process is already over." {})))
    (let [{real-id ::proposal/id :as new-proposal}
          (proposal/tx-map #::proposal{:nice-id (process.db/new-nice-id! conn slug)
                                       :title title
                                       :body body
                                       :parents (map #(find % ::proposal/id) parents)
                                       :argument-idents arguments
                                       :original-author [::user/id user-id]})
          tx-report (d/transact conn
                      (concat
                        (process.db/->enter [::process/slug slug] [::user/id user-id])
                        [(assoc new-proposal
                           :db/id "new-proposal"
                           ::proposal/opinions
                           {:db/id "authors-opinion"
                            ::opinion/value +1})
                         [:db/add [::user/id user-id] ::user/opinions "authors-opinion"]
                         [:db/add [::process/slug slug] ::process/proposals "new-proposal"]
                         [:db/add "datomic.tx" :db/txUser [::user/id user-id]]]))]
      {::proposal/id real-id
       :tempids {id real-id}
       ::p/env (assoc env :db (:db-after tx-report))})))


(def all-mutations
  [add-process
   update-process

   add-moderator

   add-participant

   add-proposal])