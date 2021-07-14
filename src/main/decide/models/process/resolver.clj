(ns decide.models.process.resolver
  (:require
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [datahike.api :as d]
    [decide.features.recommendations.api :as recommendations.api]
    [decide.models.opinion.database :as opinion.db]
    [decide.models.process :as process]
    [decide.models.process.database :as process.db]
    [decide.models.process.mutations :as process.mutations]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]
    [decide.server-components.access-plugin :as access]))

(defn get-process-entity [{:keys [db] :as env} slug]
  (let [process-ident [::process/slug slug]]
    (p/cached env process-ident
      (d/entity db process-ident))))

(defresolver resolve-all-processes [{:root/keys [public-processes private-processes]}]
  {::pc/output [{:root/all-processes [::process/slug ::process/type]}]}
  {:root/all-processes
   (concat public-processes private-processes)})

(defresolver resolve-public-processes [{:keys [db] :as env} _]
  {::pc/output [{:root/public-processes [::process/slug ::process/type]}]}
  (let [processes (vec (process.db/get-public-processes db))]
    (access/allow-many! env (map #(find % ::process/slug) processes))
    {:root/public-processes processes}))

(defresolver resolve-private-processes [{:keys [db AUTH/user-id]} _]
  {::pc/output [{:root/private-processes [::process/slug ::process/type]}]}
  {:root/private-processes
   (vec
     (if user-id
       (process.db/get-private-processes db [::user/id user-id])
       []))})

(defresolver resolve-process [env {::process/keys [slug]}]
  {::pc/input #{::process/slug}
   ::pc/output [::process/title ::process/description ::process/start-time ::process/end-time ::process/type :process/features]}
  (let [process (get-process-entity env slug)]
    (merge
      {::process/type ::process/type.public}
      (-> process
        (select-keys [::process/title ::process/description ::process/start-time ::process/end-time ::process/type :process/features])
        (update :process/features set)))))

(defresolver resolve-process-moderators [{:keys [db]} {::process/keys [slug]}]
  {::pc/input #{::process/slug}
   ::pc/output [{::process/moderators [::user/id]}]}
  (d/pull db [{::process/moderators [::user/id]}] [::process/slug slug]))

(defresolver resolve-user-moderated-processes [{:keys [db]} {::user/keys [id]}]
  {::pc/output [{::user/moderated-processes [::process/slug]}]}
  (if (user/exists? db id)
    (d/pull db [{::process/_moderators :as ::user/moderated-processes [::process/slug]}] [::user/id id])
    (throw (ex-info "User does not exist" {::user/id id}))))

(defresolver resolve-I-moderator? [{:keys [db AUTH/user-id]} {::process/keys [slug]}]
  {::pc/output [:I/moderator?]}
  {:I/moderator? (process/moderator? (d/pull db [{::process/moderators [::user/id]}] [::process/slug slug]) user-id)})

(defresolver resolve-authors [{:keys [db]} {::process/keys [proposals]}]
  {::pc/output [{::process/authors [::user/id]}
                ::process/no-of-authors]}
  (let [authors (vec
                  (set
                    (for [{::proposal/keys [id original-author]} proposals]
                      (if-let [user-id (::user/id original-author)]
                        {::user/id user-id}
                        (-> db
                          (d/pull [{::proposal/original-author [::user/id]}] [::proposal/id id])
                          ::proposal/original-author)))))]
    {::process/authors authors
     ::process/no-of-authors (count authors)}))

(defresolver resolve-no-of-participants [{:keys [db]} {::process/keys [slug]}]
  {::pc/output [::process/no-of-participants]}
  {::process/no-of-participants (process.db/get-number-of-participants db slug)})

(defresolver resolve-proposals [{:keys [db] :as env} {::process/keys [slug]}]
  {::pc/output [{::process/proposals [::proposal/id]}
                ::process/no-of-proposals]}
  (if-let [{::process/keys [proposals]} (d/pull db [{::process/proposals [::proposal/id]}] [::process/slug slug])]
    (do
      (access/allow-many! env (map #(find % ::proposal/id) proposals))
      {::process/proposals proposals
       ::process/no-of-proposals (count proposals)})
    {::process/proposals []
     ::process/no-of-proposals 0}))

(defresolver resolve-no-of-proposals [{:keys [db]} {::process/keys [slug]}]
  {::process/no-of-proposals
   (or (d/q '[:find (count ?e) .
              :in $ ?process
              :where
              [?process ::process/proposals ?e]]
         db [::process/slug slug])
     0)})

(defresolver resolve-nice-proposal [{:keys [db]} {::proposal/keys [nice-ident]}]
  {::pc/output [::proposal/id]}
  (let [[slug nice-id] nice-ident]
    {::proposal/id
     (d/q '[:find ?id .
            :in $ ?process ?nice-id
            :where
            [?process ::process/proposals ?proposal]
            [?proposal ::proposal/nice-id ?nice-id]
            [?proposal ::proposal/id ?id]]
       db [::process/slug slug] nice-id)}))

(defresolver resolve-proposal-process [{:keys [db]} {::proposal/keys [id]}]
  {::pc/output [::process/slug]}
  (::process/_proposals (d/pull db [{::process/_proposals [::process/slug]}] [::proposal/id id])))

(defresolver resolve-winner [{:keys [db]} process]
  {::pc/input #{::process/slug}
   ::pc/output [{::process/winner [::proposal/id]}]}
  (when-let [winner-proposal (process.db/get-winner db process)]
    {::process/winner (select-keys winner-proposal [::proposal/id])}))

(defresolver resolve-personal-approved-proposals [{:keys [db AUTH/user-id]} process]
  {::pc/input #{::process/slug}
   ::pc/output [{:MY/personal-proposals [::proposal/id]}]}
  (when user-id
    {:MY/personal-proposals
     (d/q '[:find [(pull ?proposal [::proposal/id]) ...]
            :in $ % ?process ?user
            :where
            [?process ::process/proposals ?proposal]
            (approves? ?user ?proposal)]
       db
       opinion.db/approves-rule
       (find process ::process/slug)
       [::user/id user-id])}))

(def all-resolvers
  [process.mutations/all-mutations

   resolve-all-processes (pc/alias-resolver2 :all-processes :root/all-processes)
   (pc/constantly-resolver ::process/available-features process/feature-set)
   resolve-public-processes
   resolve-private-processes

   resolve-process
   (pc/alias-resolver2 :process/features :process/features)
   resolve-process-moderators
   resolve-user-moderated-processes
   resolve-no-of-participants
   resolve-winner

   resolve-proposals
   resolve-no-of-proposals

   resolve-personal-approved-proposals

   resolve-authors

   resolve-I-moderator?

   recommendations.api/all-resolvers])
