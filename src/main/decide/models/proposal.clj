(ns decide.models.proposal
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [datahike.api :as d])
  (:import (java.util Date)))

(def schema
  [{:db/ident       ::id
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity
    :db/valueType   :db.type/string}

   {:db/ident       ::title
    :db/doc         "The short catchy title of a proposal."
    :db/cardinality :db.cardinality/one
    ; :db/fulltext    true
    :db/valueType   :db.type/string}

   {:db/ident       ::body
    :db/doc         "A descriptive body of the proposal."
    :db/cardinality :db.cardinality/one
    ; :db/fulltext    true
    :db/valueType   :db.type/string}

   {:db/ident       ::created
    :db/doc         "When the proposal was created."
    :db/cardinality :db.cardinality/one
    :db/valueType   :db.type/instant}

   {:db/ident       ::original-author
    :db/doc         "The user who proposed the proposal."
    :db/cardinality :db.cardinality/one
    :db/valueType   :db.type/ref}

   {:db/ident       ::parents
    :db/doc         "≥0 parent proposals from which the proposal is derived."
    :db/cardinality :db.cardinality/many
    :db/valueType   :db.type/ref}])

(s/def ::id (s/and string? (complement str/blank?)))
(s/def ::title (s/and string? (complement str/blank?)))
(s/def ::body string?)

(defn check-auth [{::pc/keys [mutate] :as mutation}]
  (assoc mutation
    ::pc/mutate
    (fn [{:keys [AUTH/user-id] :as env} params]
      (if user-id
        (mutate env params)
        (throw (ex-info "User is not logged in!" {}))))))

(defn new-proposal-id []
  (str (rand-int 1000)))

;;; API
(defmutation add-proposal [{:keys [conn AUTH/user-id] :as env} {::keys [id title body parents]}]
  {::pc/output    [::id]
   ::pc/transform check-auth}
  (let [real-id (new-proposal-id)
        proposal {::id              real-id
                  ::title           title
                  ::body            body
                  ::parents         (for [parent parents
                                          :let [id (::id parent)]]
                                      [::id id])
                  ::original-author [:user/id user-id]
                  ::created         (Date.)}
        tx-report (d/transact conn [proposal])]
    {:tempids {id real-id}
     ::p/env  (assoc env :db (:db-after tx-report))
     ::id     real-id}))

(defresolver resolve-proposal [{:keys [db]} {::keys [id]}]
  {::pc/input  #{::id}
   ::pc/output [::id ::title ::body ::created
                {::parents [::id]}
                {::original-author [:user/id]}]}
  (let [{::keys [id title body created parents original-author]}
        (d/pull db [::id ::title ::body ::created
                    {::parents [::id]}
                    {::original-author [:user/id]}]
          [::id id])]
    {::id              id
     ::title           title
     ::body            body
     ::created         created
     ::parents         (or parents [])
     ::original-author original-author}))

(defresolver all-proposal-ids [{:keys [db]} _]
  {::pc/input  #{}
   ::pc/output [{:all-proposals [::id]}]}
  {:all-proposals
   (for [id (d/q '[:find [?id ...] :where [_ ::id ?id]] db)]
     {::id id})})

(def resolvers [add-proposal resolve-proposal all-proposal-ids])