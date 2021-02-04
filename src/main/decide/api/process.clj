(ns decide.api.process
  (:require
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [datahike.api :as d]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]
    [taoensso.timbre :as log]))

(defresolver resolve-all-processes [{:keys [db]} _]
  {::pc/input #{}
   ::pc/output [{:all-processes [::process/slug]}]}
  {:all-processes
   (map #(hash-map ::process/slug %) (process/get-all-slugs db))})

(defresolver resolve-process [{:keys [db]} {::process/keys [slug]}]
  {::pc/input #{::process/slug}
   ::pc/output [::process/title ::process/description ::process/end-time]}
  (d/pull db [::process/title ::process/description ::process/end-time] [::process/slug slug]))

(defresolver resolve-process-moderators [{:keys [db]} {::process/keys [slug]}]
  {::pc/input #{::process/slug}
   ::pc/output [{::process/moderators [::user/id]}]}
  (d/pull db [{::process/moderators [::user/id]}] [::process/slug slug]))

(defresolver resolve-user-moderated-processes [{:keys [db]} {::user/keys [id]}]
  {::pc/output [{::user/moderated-processes [::process/slug]}]}
  (if (user/exists? db id)
    (d/pull db [{::process/_moderators :as ::user/moderated-processes [::process/slug]}] [::user/id id])
    (throw (ex-info "User does not exist" {::user/id id}))))

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
  {::no-of-proposals (count proposals)})

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

(def all-resolvers
  [process/resolvers

   resolve-all-processes
   resolve-process
   resolve-process-moderators
   resolve-user-moderated-processes
   resolve-no-of-contributors
   resolve-no-of-participants
   resolve-proposals
   resolve-authors])
