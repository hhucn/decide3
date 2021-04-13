(ns decide.models.proposal
  (:require
    [clojure.set :as set]
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
    [decide.schema :as schema])
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
(s/def ::pro-votes nat-int?)
(s/def ::con-votes nat-int?)
(s/def ::parents (s/coll-of ::proposal :distinct true))
(s/def ::proposal (s/keys :req [::id] :opt [::title ::body ::created ::parents ::pro-votes ::con-votes ::nice-id ::arguments]))

(s/def ::ident (s/tuple #{::id} ::id))
(s/def ::lookup (s/or :ident ::ident :db/id pos-int?))

(>defn get-arguments [db proposal-ident]
  [d.core/db? ::ident => (s/keys :req [::arguments])]
  (or
    (d/pull db [{::arguments [::argument/id]}] proposal-ident)
    {::arguments []}))

(>defn get-users-who-made-an-argument [db proposal-lookup]
  [d.core/db? ::lookup => (s/coll-of pos-int? :kind set)]
  (->> proposal-lookup
    (d/pull db [{::arguments [{::argument/author [:db/id]}]}])
    ::arguments
    (into #{} (map (comp :db/id ::argument/author)))))

(>defn get-voters [db proposal-lookup]
  [d.core/db? ::lookup => (s/coll-of pos-int? :kind set)]
  (->> proposal-lookup
    (d/pull db [{::opinions [{::user/_opinions [:db/id]}]}])
    ::opinions
    (into #{} (map (comp :db/id ::user/_opinions)))))

(>defn get-children [db proposal-ident]
  [d.core/db? ::ident => (s/coll-of (s/keys :req [::id]) :distinct true)]
  (-> db
    (d/pull [{::_parents [::id]}] proposal-ident)
    (get ::_parents [])))

(>defn get-parents [db proposal-lookup]
  [d.core/db? ::lookup => (s/coll-of (s/keys :req [::id]) :distinct true)]
  (-> db
    (d/pull [{::parents [::id]}] proposal-lookup)
    (get ::parents [])))

(defn tx-map [{::keys [id nice-id title body parents argument-idents created original-author]
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
     ::arguments argument-idents                            ; TODO check if arguments exist and belog to parents
     ::original-author original-author
     ::created created}))

(defn ->add [process-lookup {::keys [id nice-id title body parents argument-idents created original-author]
                             :or {parents []
                                  argument-idents []}}]
  (let [created (or created (Date.))
        id (or id (d.core/squuid (inst-ms created)))]
    [{:db/id (str id)
      ::id id
      ::title title
      ::nice-id nice-id
      ::body body
      ::parents parents
      ::arguments argument-idents                           ; TODO check if arguments exist and belog to parents
      ::original-author original-author
      ::created created}
     [:db/add process-lookup :decide.models.process/proposals (str id)]]))

;;; region API

(defresolver resolve-proposal [{:keys [db]} input]
  {::pc/input #{::id}
   ::pc/output [::id
                ::nice-id
                ::title ::body ::created
                {::original-author [::user/id ::user/display-name]}]
   ::pc/batch? true}
  (let [batch? (sequential? input)]
    (cond->> input
      (not batch?) vector
      :always (map #(find % ::id))
      :always (d/pull-many db [::id
                               ::nice-id
                               ::title ::body ::created
                               {::original-author [::user/id ::user/display-name]}])
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

(def voters-query
  '[:find [?user ...]
    :in $ ?proposal
    :where
    [?proposal ::opinions ?opinion]
    [?opinion :decide.models.opinion/value +1]
    [?user ::user/opinions ?opinion]])

(defn get-migration-rate [db parent-id child-id]
  [::id ::id => number?]
  (let [parent-voters (set (d/q voters-query db [::id parent-id]))
        child-voters (set (d/q voters-query db [::id child-id]))]
    (if (empty? parent-voters)
      0
      (float
        (/ (count (set/intersection parent-voters child-voters))
          (count parent-voters))))))

(defresolver resolve-child-relations [{:keys [db]} {::keys [id]}]
  {::pc/output [{:child-relations [{:proposal [::id]}
                                   :migration-rate]}]}
  {:child-relations
   (for [{child-id ::id :as child} (get-children db [::id id])]
     {:proposal child
      :migration-rate (get-migration-rate db id child-id)})})

(defresolver resolve-parent-relations [{:keys [db]} {::keys [id]}]
  {::pc/output [{:parent-relations [{:proposal [::id]}
                                    :migration-rate]}]}
  {:parent-relations
   (for [{parent-id ::id :as parent} (get-parents db [::id id])]
     {:proposal parent
      :migration-rate (get-migration-rate db parent-id id)})})

(defresolver resolve-children [{:keys [db]} {::keys [id]}]
  {::pc/input #{::id}
   ::pc/output [{::children [::id]}]}
  {::children (get-children db [::id id])})

(>defn get-pro-voting-users [db proposal-lookup]
  [d.core/db? any? => (s/coll-of pos-int? :kind set)]
  (->> proposal-lookup
    (d/q '[:find ?user
           :in $ % ?proposal
           :where
           (approves? ?user ?proposal)]
      db schema/rules)
    (map first)
    set))

(>defn get-proposals-with-shared-opinion
  "Returns a set of proposals that share at least one user who approved with both of them and the input proposal."
  [db proposal-lookup]
  [d.core/db? any? => (s/coll-of ::id :kind set)]
  (->> proposal-lookup
    (d/q '[:find [?other-uuid ...]
           :in $ ?proposal
           :where
           ;; get all users who approved input proposal
           [?proposal ::opinions ?opinion]
           [?process :decide.models.process/proposals ?proposal]
           [?opinion :decide.models.opinion/value +1]
           [?user ::user/opinions ?opinion]

           ;; get all proposals the users also approved with
           [?process :decide.models.process/proposals ?other-proposal]
           [?other-proposal ::opinions ?other-opinion]
           [?user ::user/opinions ?other-opinion]
           [?other-opinion :decide.models.opinion/value +1]

           ;; remove input proposal from result
           [?proposal ::id ?uuid]
           [?other-proposal ::id ?other-uuid]
           [(not= ?uuid ?other-uuid)]]
      db)
    set))

(defresolver resolve-similar [{:keys [db]} {::keys [id]}]
  {::pc/input #{::id}
   ::pc/output [{:similar [{:own-proposal [::id]}
                           :own-uniques
                           :common-uniques
                           {:other-proposal [::id]}
                           :other-uniques
                           :sum-uniques]}]}
  (let [own-approvers (get-pro-voting-users db [::id id])]
    {:similar
     (for [other-proposal-id (get-proposals-with-shared-opinion db [::id id])
           :let [other-approvers (get-pro-voting-users db [::id other-proposal-id])
                 own-uniques (count (set/difference own-approvers other-approvers))
                 common-uniques (count (set/intersection own-approvers other-approvers))
                 other-uniques (count (set/difference other-approvers own-approvers))]]
       {:own-proposal {::id id}
        :own-uniques own-uniques
        :common-uniques common-uniques
        :other-uniques (count (set/difference other-approvers own-approvers))
        :other-proposal {::id other-proposal-id}
        :sum-uniques (+ own-uniques common-uniques other-uniques)})}))

;;; endregion

;;; region Arguments
(defmutation add-argument
  [{:keys [conn AUTH/user-id] :as env} {::keys [id]
                                        :keys [temp-id content type]}]
  {::pc/params [::id :temp-id :content :type]
   ::pc/output [::argument/id]
   ::pc/transform auth/check-logged-in}
  (let [{real-id ::argument/id :as argument}
        (argument/tx-map
          {::argument/content content
           ::argument/type type
           :author [::user/id user-id]})
        tx-report (d/transact conn
                    [(assoc argument :db/id "new-argument")
                     [:db/add [::id id] ::arguments "new-argument"]
                     [:db/add "datomic.tx" :db/txUser [::user/id user-id]]])]
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

   resolve-child-relations
   resolve-parent-relations

   add-argument resolve-arguments resolve-parents resolve-children resolve-similar])