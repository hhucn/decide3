(ns decide.api.process
  (:require
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [datahike.api :as d]
    [decide.features.recommendations.api :as recommendations.api]
    [decide.models.opinion :as opinion]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]))

(defresolver resolve-all-processes [{:root/keys [public-processes private-processes]}]
  {::pc/output [{:root/all-processes [::process/slug ::process/type]}]}
  {:root/all-processes
   (concat public-processes private-processes)})

(defresolver resolve-public-processes [{:keys [db]} _]
  {::pc/output [{:root/public-processes [::process/slug ::process/type]}]}
  {:root/public-processes (vec (process/get-public-processes db))})

(defresolver resolve-private-processes [{:keys [db AUTH/user-id]} _]
  {::pc/output [{:root/private-processes [::process/slug ::process/type]}]}
  {:root/private-processes
   (vec
     (if user-id
       (process/get-private-processes db [::user/id user-id])
       []))})

(defresolver resolve-process [{:keys [db]} {::process/keys [slug]}]
  {::pc/input #{::process/slug}
   ::pc/output [::process/title ::process/description ::process/end-time ::process/type ::process/features]}
  (d/pull db
    [::process/title
     ::process/description
     ::process/end-time
     ::process/features
     [::process/type :default ::process/type.public]]
    [::process/slug slug]))

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
  {:I/moderator? (process/is-moderator? db [::process/slug slug] user-id)})

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

(defresolver resolve-no-of-contributors [{:keys [db]} {::process/keys [slug]}]
  {::process/no-of-contributors (process/get-no-of-contributors db slug)})

(defresolver resolve-no-of-participants [{:keys [db]} {::process/keys [slug]}]
  {::pc/output [::process/no-of-participants]}
  {::process/no-of-participants (process/get-number-of-participants db slug)})

(defresolver resolve-proposals [{:keys [db]} {::process/keys [slug]}]
  {::pc/output [{::process/proposals [::proposal/id]}]}
  (or
    (d/pull db [{::process/proposals [::proposal/id]}] [::process/slug slug])
    {::process/proposals []}))

(defresolver resolve-no-of-proposals [_ {::process/keys [proposals]}]
  {::process/no-of-proposals (count proposals)})

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
  {::process/winner (process/get-winner db process)})

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
       opinion/approves-rule
       (find process ::process/slug)
       [::user/id user-id])}))

(def all-resolvers
  [process/resolvers

   resolve-all-processes (pc/alias-resolver2 :all-processes :root/all-processes)
   (pc/constantly-resolver ::process/available-features process/available-features)
   resolve-public-processes
   resolve-private-processes

   resolve-process
   resolve-process-moderators
   resolve-user-moderated-processes
   resolve-no-of-contributors
   resolve-no-of-participants
   resolve-winner

   resolve-proposals
   resolve-no-of-proposals

   resolve-personal-approved-proposals

   resolve-authors

   resolve-I-moderator?

   recommendations.api/all-resolvers])
