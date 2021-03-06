(ns decide.models.process
  (:require
    [clojure.set :as set]
    #?(:clj  [clojure.spec.alpha :as s]
       :cljs [cljs.spec.alpha :as s])
    [com.fulcrologic.fulcro.algorithms.normalized-state :as norm-state]
    [com.fulcrologic.fulcro.raw.components :as rc]
    [com.fulcrologic.guardrails.core :refer [>def >defn => | <- ?]]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]
    [decide.process :as process]
    [decide.utils.time :as time]))

(def feature-set
  #{;;Participants may only approve to a single proposal.
    :process.feature/single-approve

    :process.feature/voting.public
    :process.feature/voting.show-nothing

    ;; Participants will be able to reject a proposal.
    :process.feature/rejects
    ;; Ask the participant to give a reason for a reject.
    :process.feature/reject-popup})

(def schema
  (concat
    [{:db/ident ::slug
      :db/valueType :db.type/string
      :db/unique :db.unique/identity
      :db/cardinality :db.cardinality/one}

     {:db/ident ::title
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one}

     {:db/ident ::description
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one}

     {:db/ident ::proposals
      :db/cardinality :db.cardinality/many
      :db/valueType :db.type/ref
      :db/isComponent true}

     {:db/ident ::type
      :db/cardinality :db.cardinality/one
      :db/valueType :db.type/keyword}

     {:db/ident ::participants
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/many}

     {:db/ident ::moderators
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/many}

     {:db/ident ::latest-id
      :db/doc "DEPRECATED: Query for latest nice-id of proposal instead."
      :db/cardinality :db.cardinality/one
      :db/valueType :db.type/long}

     {:db/ident ::end-time
      :db/cardinality :db.cardinality/one
      :db/valueType :db.type/instant}

     {:db/ident ::start-time
      :db/cardinality :db.cardinality/one
      :db/valueType :db.type/instant}

     {:db/ident :process/features
      :db/doc "Feature toggles for a process."
      :db/cardinality :db.cardinality/many
      :db/valueType :db.type/keyword}]))

(>def ::slug ::process/slug)
(>def ::title ::process/title)
(>def ::description ::process/description)
(>def ::latest-id ::process/latest-id)
(>def ::end-time (s/nilable ::process/end-time))
(>def ::start-time (s/nilable ::process/start-time))
(>def ::type (s/or ::process/type #{::type.public ::type.private}))
(>def ::feature feature-set)
(>def :process/features (s/coll-of ::feature))
(>def ::moderators (s/or
                     :legacy (s/coll-of (s/keys :req [::user/id]))
                     :main ::process/moderators))

(>def ::ident (s/tuple #{::slug} ::slug))
(>def ::lookup (s/or :ident ::ident :db/id pos-int?))
(>def ::entity (s/and associative? #(contains? % :db/id)))

(>defn moderator? [{::keys [moderators]} {::user/keys [id]}]
  [(s/keys :req [::moderators]) (s/keys :req [::user/id]) => boolean?]
  (let [moderator-ids (set (map ::user/id moderators))]
    (contains? moderator-ids id)))

(defn over?
  "Checks if a processes is over based on its end-time and the current datetime.
   Returns false if process has no end-time"
  [{::keys [end-time]}]
  [::entity => boolean?]
  (if end-time
    (time/past? end-time)
    false))

(defn started? [{::keys [start-time]}]
  (if start-time
    (time/past? start-time)
    true))

(defn running? [process]
  (and (started? process) (not (over? process))))

(>defn get-most-approved-proposals
  "From a collection of `proposals`, return a subset of `proposals` that have the most approval"
  [proposals]
  [(s/coll-of (s/keys :req [::proposal/id ::proposal/pro-votes]))
   => (s/coll-of ::proposal/proposal) | #(set/subset? (set %) (set proposals))]
  (let [voting-groups (group-by ::proposal/pro-votes proposals)
        votes (keys voting-groups)]
    (if (empty? votes)
      []
      (get voting-groups (apply max votes) []))))

(>defn remove-parents
  "From a list of `proposals`, remove all parents that have children in the collection."
  [proposals]
  [(s/coll-of (s/keys :req [::proposal/id ::proposal/parents]))
   => (s/coll-of ::proposal/proposal) | #(set/subset? (set %) (set proposals))]
  (let [parent-ids (set (map ::proposal/id (mapcat ::proposal/parents proposals)))]
    (remove (comp parent-ids ::proposal/id) proposals)))


(>defn winner
  "Determines a winner for set of proposals.
  The proposals need `::proposal/pro-votes`"
  [proposals]
  [(s/coll-of (s/keys :req [::proposal/id ::proposal/created ::proposal/pro-votes]))
   => (? ::proposal/proposal)]
  (let [most-approved-proposals (get-most-approved-proposals proposals)]
    (if (< 1 (count most-approved-proposals))
      (proposal/newest most-approved-proposals)
      (first most-approved-proposals))))

(def Basics
  (rc/nc [::slug
          ::title
          ::description
          ::type
          ::start-time
          ::end-time
          :process/features
          {::moderators [::user/id]}]
    {:componentName ::Basics
     :ident (fn [_ props] [::slug (::slug props)])}))

(defn feature-enabled? [process feature]
  (contains? (set (:process/features process)) feature))

(defn single-approve? [process]
  (feature-enabled? process :process.feature/single-approve))

(defn public-voting? [process]
  (feature-enabled? process :process.feature/voting.public))

(defn allows-rejects? [process]
  (feature-enabled? process :process.feature/rejects))

(defn show-reject-dialog? [process]
  (and
    (feature-enabled? process :process.feature/rejects)
    (feature-enabled? process :process.feature/reject-popup)))

(defn current [state]
  (norm-state/get-in-graph state [:ui/current-process]))

(defn participant? [process user]
  #?(:clj  (contains? (map :db/id (::participants process)) (:db/id user))
     :cljs (contains? (map ::user/id (::participants process)) (::user/id user))))

(defn private? [process]
  (= ::type.private (::type process)))

(defn public? [process] (not (private? process)))

(defn user-can-access-process? [user process]
  (or
    (public? process)
    (participant? process user)))

(defn must-be-running!
  "Throws if `process` is not running."
  [process]
  (when-not (running? process)
    (throw (ex-info "Process is not running" {:process (select-keys process [::slug ::start-time ::end-time])}))))

(defn must-have-access!
  "Throws if `user` hasn't access to `process`"
  [process user]
  (when-not (user-can-access-process? user process)
    (throw (ex-info "User has no access to process." {:process (select-keys process [::slug ::type])}))))