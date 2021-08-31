(ns decide.models.process
  (:require
    [clojure.set :as set]
    #?(:clj  [clojure.spec.alpha :as s]
       :cljs [cljs.spec.alpha :as s])
    [clojure.string :as str]
    [com.fulcrologic.fulcro.raw.components :as rc]
    [com.fulcrologic.guardrails.core :refer [>defn => | <- ?]]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]
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

(def slug-pattern #"^[a-z0-9]+(?:[-_][a-z0-9]+)*$")
(s/def ::slug (s/and string? (partial re-matches slug-pattern)))
(s/def ::title (s/and string? (complement str/blank?)))
(s/def ::description string?)
(s/def ::latest-id (s/and int? #(<= 0 %)))
(s/def ::end-time (s/nilable inst?))
(s/def ::start-time (s/nilable inst?))
(s/def ::type #{::type.public ::type.private})
(s/def ::feature feature-set)
(s/def :process/features (s/coll-of ::feature))
(s/def ::moderators (s/coll-of (s/keys :req [::user/id])))

(s/def ::ident (s/tuple #{::slug} ::slug))
(s/def ::lookup (s/or :ident ::ident :db/id pos-int?))
(s/def ::entity (s/and associative? #(contains? % :db/id)))

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

(>defn newest-proposal
  "Takes a collection of `proposals` and returns a single winner."
  [proposals]
  [(s/coll-of (s/keys :req [::proposal/created]) :min-count 1)
   => (s/keys :req [::proposal/created])
   | #(contains? (set proposals) %)]
  (->> proposals
    (sort-by ::proposal/created)
    reverse
    first))

(>defn winner
  "Determines a winner for set of proposals.
  The proposals need `::proposal/pro-votes`"
  [proposals]
  [(s/coll-of (s/keys :req [::proposal/id ::proposal/created ::proposal/pro-votes]))
   => (? ::proposal/proposal)]
  (let [most-approved-proposals (get-most-approved-proposals proposals)]
    (if (< 1 (count most-approved-proposals))
      (newest-proposal most-approved-proposals)
      (first most-approved-proposals))))

(defn- keep-chars [s re]
  (apply str (re-seq re s)))

(>defn slugify [s]
  [(s/and string? (complement str/blank?)) => ::slug]
  (-> s
    str/lower-case
    str/trim
    (str/replace #"[\s-]+" "-")                             ; replace multiple spaces and dashes with a single dash
    (keep-chars #"[a-z0-9-]")
    (str/replace #"^-|" "")))                               ; remove dash prefix

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
