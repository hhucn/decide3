(ns decide.models.opinion.database
  (:require
    [clojure.spec.alpha :as s]
    [com.fulcrologic.guardrails.core :refer [>defn >defn- =>]]
    [datahike.api :as d]
    [datahike.core :as d.core]
    [decide.models.opinion :as opinion]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]))

(def approves-rule
  '[[(approves? ?user ?proposal)
     [?user ::user/opinions ?opinion]
     [?proposal ::proposal/opinions ?opinion]
     [?opinion ::opinion/value +1]]
    [(undecided? ?user ?proposal)
     (or-join [?user ?proposal]
       (not
         [?user ::user/opinions ?opinion]
         [?proposal ::proposal/opinions ?opinion])
       (and
         [?user ::user/opinions ?opinion]
         [?proposal ::proposal/opinions ?opinion]
         [?opinion ::opinion/value 0]))]])

(>defn get-opinion [db user-ident proposal-ident]
  [d.core/db? ::user/lookup ::proposal/ident => (s/nilable nat-int?)]
  (d/q '[:find ?opinion .
         :in $ ?user ?proposal
         :where
         [?user ::user/opinions ?opinion]
         [?proposal ::proposal/opinions ?opinion]]
    db
    user-ident
    proposal-ident))

(>defn- ->set-value
  "Generate a transaction to set `value` as an opinion of a user for a proposal.
  DOES NOT VALIDATE ANYTHING!"
  [db user-ref proposal-ref value]
  [d.core/db? ::user/ident ::proposal/ident ::value => vector?]
  (if-let [id (get-opinion db user-ref proposal-ref)]
    [{:db/id id ::opinion/value value}]
    [[:db/add proposal-ref ::proposal/opinions "temp"]
     [:db/add user-ref ::user/opinions "temp"]
     {:db/id "temp" ::opinion/value value}]))

(>defn ->all-neutral
  "Generate a transaction to set `value` of all proposals by a user for a process to the neutral value (0)."
  [db user-ref process-ref]
  [d.core/db? ::user/ident ::process/ident ::value => vector?]
  (mapv #(hash-map :db/id % ::opinion/value 0)
    (d/q '[:find [?e ...]
           :in $ ?user ?process
           :where
           [?user ::user/opinions ?e]
           [?process ::process/proposals ?proposal]
           [?proposal ::proposal/opinions ?e]]
      db user-ref process-ref)))

(>defn ->set [db user-ref proposal-ref value]
  [d.core/db? ::user/ident ::proposal/ident ::value => vector?]
  (let [process (::process/_proposals (d/pull db [{::process/_proposals [::process/slug ::process/features]}] proposal-ref))]
    (into [] cat
      [(when (process/single-approve? process)
         (->all-neutral db user-ref (find process ::process/slug)))
       (->set-value db user-ref proposal-ref value)])))

(defn get-values-for-proposal [db proposal-ident]
  (merge
    {1 0, -1 0}                                             ; default values
    (->> proposal-ident
      (d/pull db [{::proposal/opinions [::opinion/value]}])
      ::proposal/opinions
      (map :decide.models.opinion/value)
      frequencies)))