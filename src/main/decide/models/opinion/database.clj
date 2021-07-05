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

(>defn get-opinion [db user proposal]
  [d.core/db? (s/keys :req [:db/id]) (s/keys :req [:db/id]) => (s/nilable nat-int?)]
  (d/q '[:find ?opinion .
         :in $ ?user ?proposal
         :where
         [?user ::user/opinions ?opinion]
         [?proposal ::proposal/opinions ?opinion]]
    db
    (:db/id user)
    (:db/id proposal)))

(>defn- ->set-value
  "Generate a transaction to set `value` as an opinion of a user for a proposal.
  DOES NOT VALIDATE ANYTHING!"
  [db user proposal value]
  [d.core/db? (s/keys :req [:db/id]) (s/keys :req [:db/id]) ::opinion/value => vector?]
  (if-let [id (get-opinion db user proposal)]
    [{:db/id id ::opinion/value value}]
    [[:db/add (:db/id proposal) ::proposal/opinions "temp"]
     [:db/add (:db/id user) ::user/opinions "temp"]
     {:db/id "temp" ::opinion/value value}]))

(>defn ->all-neutral
  "Generate a transaction to set `value` of all proposals by a user for a process to the neutral value (0)."
  [db user process]
  [d.core/db? (s/keys :req [:db/id]) (s/keys :req [:db/id]) => vector?]
  (mapv #(hash-map :db/id % ::opinion/value 0)
    (d/q '[:find [?e ...]
           :in $ ?user ?process
           :where
           [?user ::user/opinions ?e]
           [?process ::process/proposals ?proposal]
           [?proposal ::proposal/opinions ?e]]
      db (:db/id user) (:db/id process))))

(defn ->set [db user process proposal value]
  [d.core/db? (s/keys :req [:db/id]) (s/keys :req [:db/id]) (s/keys :req [:db/id]) ::opinion/value => vector?]
  (concat
    (when (process/single-approve? process)
      (->all-neutral db user (find process ::process/slug)))
    (->set-value db user proposal value)))

(defn get-values-for-proposal [db proposal-ident]
  (merge
    {1 0, -1 0}                                             ; default values
    (->> proposal-ident
      (d/pull db [{::proposal/opinions [::opinion/value]}])
      ::proposal/opinions
      (map :decide.models.opinion/value)
      frequencies)))