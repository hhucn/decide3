(ns decide.models.proposal
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
            [com.wsscode.pathom.core :as p]
            [datahike.api :as d]
            [decide.models.opinion :as opinion])
  (:import (java.util Date)))

(def schema
  [{:db/ident       :proposal/id
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity
    :db/valueType   :db.type/string}

   {:db/ident       :proposal/title
    :db/doc         "The short catchy title of a proposal."
    :db/cardinality :db.cardinality/one
    ; :db/fulltext    true
    :db/valueType   :db.type/string}

   {:db/ident       :proposal/body
    :db/doc         "A descriptive body of the proposal."
    :db/cardinality :db.cardinality/one
    ; :db/fulltext    true
    :db/valueType   :db.type/string}

   {:db/ident       :proposal/created
    :db/doc         "When the proposal was created."
    :db/cardinality :db.cardinality/one
    :db/valueType   :db.type/instant}

   {:db/ident       :proposal/original-author
    :db/doc         "The user who proposed the proposal."
    :db/cardinality :db.cardinality/one
    :db/valueType   :db.type/ref}

   {:db/ident       :proposal/parents
    :db/doc         "≥0 parent proposals from which the proposal is derived."
    :db/cardinality :db.cardinality/many
    :db/valueType   :db.type/ref}])

(s/def :proposal/id (s/and string? (complement str/blank?)))
(s/def :proposal/title (s/and string? (complement str/blank?)))
(s/def :proposal/body string?)

(defn check-auth [{::pc/keys [mutate] :as mutation}]
  (assoc mutation
    ::pc/mutate
    (fn [{:keys [AUTH/profile-nickname] :as env} params]
      (if profile-nickname
        (mutate env params)
        (throw (ex-info "User is not logged in!" {}))))))

(defn new-proposal-id []
  (str (rand-int 1000)))

;;; API
(defmutation add-proposal [{:keys [conn AUTH/profile-nickname] :as env} {:proposal/keys [id title body parents]}]
  {::pc/output    [:proposal/id]
   ::pc/transform check-auth}
  (let [real-id (new-proposal-id)
        proposal #:proposal{:id              real-id
                            :title           title
                            :body            body
                            :parents         (for [parent parents
                                                   :let [id (:proposal/id parent)]]
                                               [:proposal/id id])
                            :original-author [:profile/nickname profile-nickname]
                            :created         (Date.)}
        tx-report (d/transact conn [proposal])]
    {:tempids     {id real-id}
     ::p/env      (assoc env :db (:db-after tx-report))
     :proposal/id real-id}))

(defmutation add-opinion [{:keys [conn AUTH/profile-nickname] :as env} {:keys [proposal/id opinion]}]
  {::pc/params [:proposal/id :opinion]
   ::pc/transform check-auth}
  (let [tx-report (opinion/set-opinion! conn profile-nickname id opinion)]
    {::p/env (assoc env :db (:db-after tx-report))}))

(defresolver resolve-proposal-opinions [{:keys [db]} {:keys [proposal/id]}]
  {::pc/input  #{:proposal/id}
   ::pc/output [:proposal/pro-votes :proposal/con-votes]}
  (let [opinions
        (d/q
          '[:find (clojure.core/frequencies ?values) .
            :in $ ?id
            :where
            [?e :proposal/id ?id]
            [?e :proposal/opinions ?opinions]
            [?opinions :opinion/value ?values]]
          db id)]
    #:proposal{:pro-votes (get opinions 1 0)
               :con-votes (get opinions -1 0)}))

(defresolver resolve-personal-opinion [{:keys [db AUTH/profile-nickname]} {:keys [proposal/id]}]
  {::pc/input  #{:proposal/id}
   ::pc/output [:proposal/opinion]}
  (let [opinion-value
        (opinion/pull-personal-opinion db [:proposal/id id] [:profile/nickname profile-nickname])]
    #:proposal{:opinion (or opinion-value 0)}))

(defresolver proposal-resolver [{:keys [db]} {:keys [proposal/id]}]
  {::pc/input  #{:proposal/id}
   ::pc/output [:proposal/id :proposal/title :proposal/body :proposal/created
                {:proposal/parents [:proposal/id]}
                {:proposal/original-author [:profile/nickname]}]}
  (let [{:proposal/keys [id title body created parents original-author]}
        (d/pull db [:proposal/id :proposal/title :proposal/body :proposal/created
                    {:proposal/parents [:proposal/id]}
                    {:proposal/original-author [:profile/nickname]}]
          [:proposal/id id])]
    #:proposal{:id              id
               :title           title
               :body            body
               :created         created
               :parents         (or parents [])
               :original-author original-author}))

(defresolver all-proposal-ids [{:keys [db]} _]
  {::pc/input  #{}
   ::pc/output [{:all-proposals [:proposal/id]}]}
  {:all-proposals
   (for [id (d/q '[:find [?id ...] :where [_ :proposal/id ?id]] db)]
     {:proposal/id id})})

(def resolvers [add-proposal proposal-resolver all-proposal-ids resolve-proposal-opinions add-opinion resolve-personal-opinion])