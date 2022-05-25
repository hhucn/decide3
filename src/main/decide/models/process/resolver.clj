(ns decide.models.process.resolver
  (:require
   [com.wsscode.pathom.connect :as pc :refer [defresolver]]
   [com.wsscode.pathom.core :as p]
   [datahike.api :as d]
   [decide.features.recommendations.api :as recommendations.api]
   [decide.models.opinion.database :as opinion.db]
   [decide.models.process :as process]
   [decide.models.process.database :as process.db]
   [decide.models.process.mutations :as process.mutations]
   [decide.models.proposal :as-alias proposal]
   [decide.models.user :as user]
   [me.ebbinghaus.pathom-access-plugin.api :as access]))

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

(defresolver resolve-I-moderator? [{:keys [db AUTH/user-id]} {::process/keys [slug]}]
  {::pc/output [:I/moderator?]}
  {:I/moderator? (process/moderator? (d/pull db [{::process/moderators [::user/id]}] [::process/slug slug]) user-id)})

(defresolver resolve-no-of-participants [{:keys [db]} {::process/keys [slug]}]
  {::pc/output [::process/no-of-participants]}
  {::process/no-of-participants (process.db/get-number-of-participants db slug)})

(defresolver resolve-participants [env {::process/keys [slug]}]
  {::pc/output [{::process/participants [::user/id ::user/display-name]}]}
  (let [process (get-process-entity env slug)]
    {::process/participants
     (map
       #(select-keys % [::user/id ::user/display-name])
       (::process/participants process))}))

(defresolver resolve-proposals [{:keys [db] :as env} {::process/keys [slug]}]
  {::pc/output [{::process/proposals
                 [::proposal/id
                  ::process/slug
                  {::proposal/process [::process/slug]}]}
                ::process/no-of-proposals]}
  (if-let [{::process/keys [proposals]} (d/pull db [{::process/proposals [::proposal/id]}] [::process/slug slug])]
    (do
      (access/allow-many! env (map #(find % ::proposal/id) proposals))
      {::process/proposals (map #(assoc % ::process/slug slug
                                          ::proposal/process {::process/slug slug})
                             proposals)
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
  {::pc/output [::process/slug
                {::proposal/process [::process/slug]}]}
  (let [process (::process/_proposals (d/pull db [{::process/_proposals [::process/slug]}] [::proposal/id id]))]
    (assoc process ::proposal/process process)))

(defresolver resolve-winner [{:keys [db]} process]
  {::pc/input #{::process/slug}
   ::pc/output [{::process/winner [::proposal/id]}]}
  (when-let [winner-proposal (process.db/get-winner db process)]
    {::process/winner (select-keys winner-proposal [::proposal/id])}))

(defresolver resolve-personal-approved-proposals [{:keys [db AUTH/user]} process]
  {::pc/input #{::process/slug}
   ::pc/output [{:MY/personal-proposals [::proposal/id]}]}
  (when user
    {:MY/personal-proposals
     (d/q '[:find [(pull ?proposal [::proposal/id]) ...]
            :in $ % ?process ?user
            :where
            [?process ::process/proposals ?proposal]
            (approves? ?user ?proposal)]
       db
       opinion.db/rules
       (find process ::process/slug)
       (:db/id user))}))

(defresolver resolve-total-votes [env {::process/keys [slug]}]
  {::pc/output [:process/total-votes]}
  (let [process (get-process-entity env slug)]
    {:process/total-votes (process.db/total-votes process)}))

(def all-resolvers
  [process.mutations/all-mutations

   resolve-all-processes (pc/alias-resolver2 :all-processes :root/all-processes)
   (pc/constantly-resolver ::process/available-features process/feature-set)
   resolve-public-processes
   resolve-private-processes

   resolve-process
   (pc/alias-resolver2 :process/features :process/features)
   resolve-process-moderators
   resolve-participants
   resolve-no-of-participants
   resolve-winner

   resolve-proposals
   resolve-proposal-process
   resolve-no-of-proposals

   resolve-total-votes

   resolve-personal-approved-proposals

   resolve-I-moderator?

   recommendations.api/all-resolvers])
