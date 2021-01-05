(ns decide.models.proposal
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [com.fulcrologic.guardrails.core :refer [>defn => | <-]]
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [datahike.api :as d]
    [datahike.core :as d.core]
    [decide.models.argument :as argument]
    [decide.models.authorization :as auth]
    [decide.models.user :as user]
    [taoensso.timbre :as log])
  (:import (java.util Date)))

(def schema
  [{:db/ident ::id
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity
    :db/valueType :db.type/uuid}

   {:db/ident ::nice-id
    :db/cardinality :db.cardinality/one
    :db/valueType :db.type/long}

   {:db/ident ::title
    :db/doc "The short catchy title of a proposal."
    :db/cardinality :db.cardinality/one
    ; :db/fulltext    true
    :db/valueType :db.type/string}

   {:db/ident ::body
    :db/doc "A descriptive body of the proposal."
    :db/cardinality :db.cardinality/one
    ; :db/fulltext    true
    :db/valueType :db.type/string}

   {:db/ident ::created
    :db/doc "When the proposal was created."
    :db/cardinality :db.cardinality/one
    :db/valueType :db.type/instant}

   {:db/ident ::original-author
    :db/doc "The user who proposed the proposal."
    :db/cardinality :db.cardinality/one
    :db/valueType :db.type/ref}

   {:db/ident ::parents
    :db/doc "≥0 parent proposals from which the proposal is derived."
    :db/cardinality :db.cardinality/many
    :db/valueType :db.type/ref}

   {:db/ident ::arguments
    :db/cardinality :db.cardinality/many
    :db/valueType :db.type/ref}])

(s/def ::id uuid?)
(s/def ::nice-id pos-int?)
(s/def ::title (s/and string? (complement str/blank?)))
(s/def ::body string?)
(s/def ::created inst?)
(s/def ::arguments (s/coll-of (s/keys
                                :req [::argument/id]
                                :opt [::argument/content])
                     :distinct true))

(s/def ::ident (s/tuple #{::id} ::id))

(>defn get-arguments [db proposal-ident]
  [d.core/db? ::ident => (s/keys :req [::arguments])]
  (or
    (d/pull db [{::arguments [::argument/id]}] proposal-ident)
    {::arguments []}))

(>defn get-children [db proposal-ident]
  [d.core/db? ::ident => (s/coll-of (s/keys :req [::id]) :distinct true)]
  (-> db
    (d/pull [{::_parents [::id]}] proposal-ident)
    (get ::_parents [])))

(>defn tx-map [{::keys [id nice-id title body parents argument-idents created original-author]
                :or {parents []
                     argument-idents []}}]
  [(s/keys :req [::title ::body ::nice-id]
     :opt [::id])
   => (s/keys :req [::id ::title ::nice-id ::body ::created])]
  (let [created (or created (Date.))]
    {::id (or id (d.core/squuid (inst-ms created)))
     ::title title
     ::nice-id nice-id
     ::body body
     ::parents parents
     ::arguments argument-idents                     ; TODO check if arguments exist and belog to parents
     ::original-author original-author
     ::created created}))

;;; region API

(defresolver resolve-proposal [{:keys [db]} input]
  {::pc/input #{::id}
   ::pc/output [::id
                ::nice-id
                ::title ::body ::created
                {::original-author [:decide.models.user/id]}]
   ::pc/batch? true}
  (let [batch? (sequential? input)]
    (cond->> input
      (not batch?) vector
      :always (map #(find % ::id))
      :always (d/pull-many db [::id
                               ::nice-id
                               ::title ::body ::created
                               {::original-author [:decide.models.user/id]}])
      (not batch?) first)))

(defresolver resolve-parents [{:keys [db]} {::keys [id]}]
  {::pc/input #{::id}
   ::pc/output [{::parents [::id]}]}
  (or (d/pull db [{::parents [::id]}] [::id id]) {::parents []}))

(defresolver resolve-all-proposal-ids [{:keys [db]} _]
  {::pc/input #{}
   ::pc/output [{:all-proposals [::id]}]}
  {:all-proposals
   (for [id (d/q '[:find [?id ...] :where [_ ::id ?id]] db)]
     {::id id})})

(defresolver resolve-children [{:keys [db]} {::keys [id]}]
  {::pc/input #{::id}
   ::pc/output [{::children [::id]}]}
  {::children (get-children db [::id id])})
;;; endregion

;;; region Arguments
(defmutation add-argument
  [{:keys [conn AUTH/user-id] :as env} {::keys [id]
                                        :keys [temp-id content]}]
  {::pc/params [::id :temp-id :content]
   ::pc/output [::argument/id]
   ::pc/transform auth/check-logged-in}
  (let [{real-id ::argument/id :as argument}
        (argument/tx-map
          {::argument/content content
           :author [::user/id user-id]})
        tx-report (d/transact conn
                    [(assoc argument :db/id "new-argument")
                     [:db/add [::id id] ::arguments "new-argument"]])]
    {:tempids {temp-id real-id}
     ::p/env (assoc env :db (:db-after tx-report))
     ::argument/id real-id}))

(defresolver resolve-arguments [{:keys [db]} {::keys [id]}]
  {::pc/input  #{::id}
   ::pc/output [{::arguments [::argument/id]}]}
  (get-arguments db [::id id]))
;;; endregion

(def resolvers
  [resolve-proposal resolve-all-proposal-ids
   add-argument resolve-arguments resolve-parents resolve-children])