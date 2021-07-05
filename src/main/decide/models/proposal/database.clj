(ns decide.models.proposal.database
  (:require
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [com.fulcrologic.guardrails.core :refer [>defn => | <-]]
    [datahike.api :as d]
    [datahike.core :as d.core]
    [decide.models.argumentation.database :as argumentation.db]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]
    [decide.schema :as schema]
    [decide.utils.validation :as utils.validation])
  (:import (java.util Date)))

(>defn get-children [db proposal-ident]
  [d.core/db? ::proposal/ident => (s/coll-of (s/keys :req [::proposal/id]) :distinct true)]
  (-> db
    (d/pull [{::proposal/_parents [::proposal/id]}] proposal-ident)
    (get ::proposal/_parents [])))

(>defn get-parents [db proposal-lookup]
  [d.core/db? ::proposal/lookup => (s/coll-of (s/keys :req [::proposal/id]) :distinct true)]
  (-> db
    (d/pull [{::proposal/parents [::proposal/id]}] proposal-lookup)
    (get ::proposal/parents [])))

(>defn get-generation
  "Query for the generation of a proposal.

  A proposal without parents has generation = 0.
  A child of that proposal would have generation = 1.
  When a proposal has multiple parents, the generation is one higher than the highest generation of parents."
  [db proposal]
  [d.core/db? (s/keys :req [::proposal/id]) => nat-int?]
  (d/q
    '[:find ?gen .
      :in $ % ?proposal
      :where
      (generation ?proposal ?gen)]
    db
    '[[(generation ?proposal ?gen)
       [(ground 0) ?gen]
       (not [?proposal ::proposal/parents])]
      [(generation ?proposal ?gen)
       [?proposal ::proposal/parents ?parent]
       (generation ?parent ?pgen)
       [(max ?pgen) ?max-pgen]
       [(inc ?max-pgen) ?gen]]]


    (find proposal ::proposal/id)))

(def voters-query
  '[:find [?user ...]
    :in $ ?proposal
    :where
    [?proposal ::proposal/opinions ?opinion]
    [?opinion :decide.models.opinion/value +1]
    [?user ::user/opinions ?opinion]])

(defn get-migration-rate [db parent-id child-id]
  [::proposal/id ::proposal/id => number?]
  (let [parent-voters (set (d/q voters-query db [::proposal/id parent-id]))
        child-voters (set (d/q voters-query db [::proposal/id child-id]))]
    (if (empty? parent-voters)
      0
      (float
        (/ (count (set/intersection parent-voters child-voters))
          (count parent-voters))))))

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
  [d.core/db? any? => (s/coll-of ::proposal/id :kind set)]
  (->> proposal-lookup
    (d/q '[:find [?other-uuid ...]
           :in $ ?proposal
           :where
           ;; get all users who approved input proposal
           [?proposal ::proposal/opinions ?opinion]
           [?process :decide.models.process/proposals ?proposal]
           [?opinion :decide.models.opinion/value +1]
           [?user ::user/opinions ?opinion]

           ;; get all proposals the users also approved with
           [?process :decide.models.process/proposals ?other-proposal]
           [?other-proposal ::proposal/opinions ?other-opinion]
           [?user ::user/opinions ?other-opinion]
           [?other-opinion :decide.models.opinion/value +1]

           ;; remove input proposal from result
           [?proposal ::proposal/id ?uuid]
           [?other-proposal ::proposal/id ?other-uuid]
           [(not= ?uuid ?other-uuid)]]
      db)
    set))


(>defn get-users-who-made-an-argument [db proposal-lookup]
  [d.core/db? ::proposal/lookup => (s/coll-of pos-int? :kind set)]
  (set (d/q '[:find [?author ...]
              :in $ % ?proposal
              :where
              (belongs-to-proposal ?argument ?proposal)
              [?argument :argument/premise ?premise]
              [?premise :author ?author]]
         db argumentation.db/argumentation-rules proposal-lookup)))

(>defn get-voters [db proposal-lookup]
  [d.core/db? ::proposal/lookup => (s/coll-of pos-int? :kind set)]
  (->> proposal-lookup
    (d/pull db [{::proposal/opinions [{::user/_opinions [:db/id]}]}])
    ::proposal/opinions
    (into #{} (map (comp :db/id ::user/_opinions)))))

(def argument-member-rules
  '[[(sub-argument ?argument ?sub-argument)
     [?argument :argument/premise ?premise]
     [?sub-argument :argument/conclusion ?premise]]

    [(argument-member ?proposal ?argument)
     [?proposal ::proposal/arguments ?first-level-argument]
     (or
       [(ground ?first-level-argument) ?argument]
       (argument-member ?first-level-argument ?argument))]

    [(argument-member ?argument ?flat-sub-arguments)
     (sub-argument ?argument ?sub-arguments)
     (or
       [(ground ?sub-arguments) ?flat-sub-arguments]
       (argument-member ?sub-arguments ?flat-sub-arguments))]])

(>defn get-no-of-arguments [db proposal]
  [d.core/db? (s/keys :req [::proposal/id]) => nat-int?]
  (or (d/q
        '[:find (count-distinct ?argument) .
          :in $ % ?proposal
          :where
          (argument-member ?proposal ?argument)]
        db
        argument-member-rules
        (find proposal ::proposal/id))
    0))

(>defn get-no-of-sub-arguments [db argument]
  [d.core/db? (s/keys :req [:argument/id]) => nat-int?]
  (or
    (d/q
      '[:find (count-distinct ?sub-argument) .
        :in $ % ?argument
        :where
        (argument-member ?argument ?sub-argument)]
      db
      argument-member-rules
      (find argument :argument/id))
    0))

(>defn exists?
  [db proposal-id]
  [d.core/db? ::proposal/id => boolean?]
  (some? (d/q '[:find ?e . :in $ ?proposal-id :where [?e ::proposal/id ?proposal-id]] db proposal-id)))

(defn belongs-to-process? [proposal process]
  (contains? (::process/proposals process) proposal))

(defn new-base [{:keys [id title body created]}]
  (utils.validation/validate ::proposal/title title "Title not valid")
  (utils.validation/validate ::proposal/body body "Body not valid")

  (let [created (or created (Date.))
        id (or id (d.core/squuid (inst-ms created)))]
    #::proposal{:db/id (str id) ; tempid
                :id (or id (d.core/squuid (inst-ms created)))
                :title title
                :body body
                :created created}))

