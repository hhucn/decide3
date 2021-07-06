(ns decide.models.user.api
  (:require
    [clojure.spec.alpha :as s]
    [com.fulcrologic.guardrails.core :refer [>defn >defn- => | ? <-]]
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [datahike.api :as d]
    [datahike.core :as d.core]
    [decide.models.user :as user]
    [taoensso.timbre :as log]
    [clojure.string :as str]))

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

(defresolver resolve-nickname-to-id [{:keys [db]} {:user/keys [nickname]}]
  {::pc/output [:user/id]}
  (d/pull db [[::user/id :as :user/id]] [::user/email nickname]))

(defresolver resolve-public-infos [{:keys [db]} {::user/keys [id]}]
  {::pc/input #{::user/id}
   ::pc/output [::user/display-name :user/nickname]}
  (d/pull db
    [::user/display-name [::user/email :as :user/nickname]]
    [::user/id id]))

(defresolver resolve-private-infos [{:keys [db AUTH/user-id]} {::user/keys [id]}]
  {::pc/output [:user/email]}
  (if (= user-id id)
    (d/pull db [:user/email] [::user/id id])
    (do
      (log/warn (format "%s tried to resolve :user/mail for %s" (str user-id) (str id)))
      nil)))

(defmutation update-user [{:keys [conn AUTH/user-id] :as env} {::user/keys [id display-name]
                                                               :user/keys [email]
                                                               :as user}]
  {::pc/params [::user/id ::user/display-name :user/email]
   ::pc/output [:user/id]}
  (let [valid-spec (s/keys :req [::user/id] :opt [:user/email ::user/display-name])
        user (select-keys user [::user/id ::user/display-name :user/email])]
    (when (= user-id id)
      (if (s/valid? valid-spec user)
        (let [{:keys [db-after]}
              (d/transact conn
                {:tx-data
                 [(-> user
                    (dissoc ::user/id)
                    (assoc :db/id [::user/id id]))
                  (when (str/blank? email)
                    [:db/retract [::user/id id] :user/email])]})]
          {::p/env (assoc env :db db-after)
           :user/id id})
        (throw (ex-info "Malformed user data" (select-keys (s/explain-data valid-spec user) [::s/problems])))))))


(def all-resolvers
  [autocomplete-user
   resolve-public-infos
   resolve-private-infos

   resolve-nickname-to-id

   update-user
   (pc/alias-resolver2 ::user/display-name :user/display-name)
   (pc/alias-resolver2 ::user/email :user/nickname)
   (pc/alias-resolver2 ::user/id :user/id)])
