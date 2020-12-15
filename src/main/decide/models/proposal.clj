(ns decide.models.proposal
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [datahike.api :as d]
    [datahike.core :as d.core]
    [decide.models.argument :as argument]
    [decide.models.authorization :as auth]
    [ghostwheel.core :refer [>defn => | <-]]
    [taoensso.timbre :as log])
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
    :db/valueType   :db.type/ref}

   {:db/ident       ::arguments
    :db/cardinality :db.cardinality/many
    :db/valueType   :db.type/ref}])

(s/def ::id (s/and string? (complement str/blank?)))
(s/def ::title (s/and string? (complement str/blank?)))
(s/def ::body string?)
(s/def ::created inst?)
(s/def ::arguments (s/coll-of (s/keys
                                :req [::argument/id]
                                :opt [::argument/content])
                     :distinct true))

(s/def ::ident (s/tuple #{::id} ::id))

(>defn new-proposal-id []
  [=> string?]
  (str (rand-int 1000)))

(>defn get-arguments [db proposal-ident]
  [d.core/db? ::ident => (s/keys :req [::arguments])]
  (or (d/pull db [{::arguments [::argument/id]}] proposal-ident)
    {::arguments []}))

;;; region API

(defn tx-data-add [{::keys [id title body parents argument-idents user-ident]}]
  {:db/id            (str id)
   ::id              id
   ::title           title
   ::body            body
   ::parents         (for [parent parents
                           :let [id (::id parent)]]
                       [::id id])
   ::arguments       argument-idents                  ; TODO check if arguments exist and belog to parents
   ::original-author user-ident
   ::created         (Date.)})

(defmutation add [{:keys [conn AUTH/user-id] :as env} {::keys [id title body parents arguments process]
                                                       :or    {parents [] arguments []}}]
  {::pc/params    [::id ::title ::body ::parents ::arguments]
   ::pc/output    [::id]
   ::pc/transform auth/check-logged-in}
  (let [real-id (new-proposal-id)
        proposal {:db/id            "temp"
                  ::id              real-id
                  ::title           title
                  ::body            body
                  ::parents         (for [parent parents
                                          :let [id (::id parent)]]
                                      [::id id])
                  ::arguments       (vec arguments)         ; TODO check if arguments exist and belog to parents
                  ::original-author [:user/id user-id]
                  ::created         (Date.)}
        tx-report (d/transact conn
                    [proposal
                     [:db/add process :decide.models.process/proposals "temp"]])]
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

(defresolver resolve-all-proposal-ids [{:keys [db]} _]
  {::pc/input  #{}
   ::pc/output [{:all-proposals [::id]}]}
  {:all-proposals
   (for [id (d/q '[:find [?id ...] :where [_ ::id ?id]] db)]
     {::id id})})
;;; endregion

;;; region Arguments
(defmutation add-argument
  [{:keys [conn AUTH/user-id] :as env} {::keys [id]
                                        :keys  [temp-id content]}]
  {::pc/params    [::id :temp-id :content]
   ::pc/output    [::argument/id]
   ::pc/transform auth/check-logged-in}
  (let [real-id (d.core/squuid)
        statement {:db/id             "temp"
                   ::argument/id      real-id
                   ::argument/content content
                   ::argument/author  [:user/id user-id]}
        tx-report (d/transact conn
                    [statement
                     [:db/add [::id id] ::arguments "temp"]])]
    {:tempids      {temp-id real-id}
     ::p/env       (assoc env :db (:db-after tx-report))
     ::argument/id real-id}))

(defresolver resolve-arguments [{:keys [db]} {::keys [id]}]
  {::pc/input  #{::id}
   ::pc/output [{::arguments [::argument/id]}]}
  (get-arguments db [::id id]))
;;; endregion

(def resolvers
  [add resolve-proposal resolve-all-proposal-ids
   add-argument resolve-arguments])