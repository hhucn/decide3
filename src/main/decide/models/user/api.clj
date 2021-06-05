(ns decide.models.user.api
  (:require
    [clojure.spec.alpha :as s]
    [com.fulcrologic.guardrails.core :refer [>defn >defn- => | ? <-]]
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [datahike.api :as d]
    [datahike.core :as d.core]
    [decide.models.user :as user]))

(>defn search-for-user [db search-term]
  [d.core/db? string? => (s/coll-of (s/keys :req [::user/id]) :distinct true)]
  (d/q '[:find [(pull ?e [::user/id ::user/display-name]) ...]
         :in $ ?search-term
         :where
         [?e ::user/display-name ?dn]
         [(clojure.string/lower-case ?dn) ?lc-dn]
         [(clojure.string/lower-case ?search-term) ?lc-?search-term]
         [(clojure.string/includes? ?lc-dn ?lc-?search-term)]]
    db (str search-term)))

(defresolver autocomplete-user [{:keys [db] :as env} _]
  {::pc/params [:term :limit]
   ::pc/output [{:autocomplete/users [::user/id ::user/display-name]}]}
  (let [{:keys [term limit] :or {limit 3}} (-> env :ast :params)
        limit (min limit 5)]
    {:autocomplete/users
      (take limit (search-for-user db term))}))



(comment
  (require '[decide.server-components.database :refer [conn]])

  (d/q '[:find (pull ?e [::user/display-name])
         :in $ ?search-term
         :where
         [?e ::user/display-name ?dn]
         [(clojure.string/lower-case ?dn) ?lc-dn]
         [(clojure.string/lower-case ?search-term) ?lc-?search-term]
         [(clojure.string/includes? ?lc-dn ?lc-?search-term)]]
    @conn "mar")

  (search-for-user @conn nil))

(defresolver resolve-own-infos [{:keys [db AUTH/user-id]} {::user/keys [id]}]
  {::pc/output [::user/email]}
  (when (= user-id id)
    (d/pull db [::user/email] [::user/id id])))

(defmutation update-user [{:keys [conn AUTH/user-id] :as env} {::user/keys [id display-name email] :as user}]
  {}
  (let [valid-spec (s/keys :req [::user/id])
        user (select-keys user [::user/id ::user/display-name #_::user/email])] ; TODO allow changing of email once nickname and email are separate
    (when (= user-id id)
      (if (s/valid? valid-spec user)
        (let [{:keys [db-after]} (d/transact! conn
                                   (-> user
                                     (dissoc ::user/id)
                                     (assoc :db/id [::user/id id])))]
          {::p/env (assoc env :db {:db-after db-after})})
        (throw (ex-info "Malformed user data" (select-keys (s/explain-data valid-spec user) [::s/problems])))))))


(def all-resolvers
  [autocomplete-user
   resolve-own-infos

   update-user
   (pc/alias-resolver ::user/email ::user/nickname)]) ; TODO remove once nickname and email are separate
