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

(>defn get-opinion-eid [db user proposal]
  [d.core/db? ::user/entity ::proposal/entity => (s/nilable nat-int?)]
  (d/q '[:find ?opinion .
         :in $ ?user ?proposal
         :where
         [?user ::user/opinions ?opinion]
         [?proposal ::proposal/opinions ?opinion]]
    db
    (:db/id user)
    (:db/id proposal)))

(>defn- ->set-value
  "Generate a transaction to set `new-value` as an opinion of a user for a proposal.
  DOES NOT VALIDATE ANYTHING!"
  [db user proposal new-value]
  [d.core/db? ::user/entity ::proposal/entity ::opinion/value => vector?]
  (let [id (get-opinion-eid db user proposal)
        old-value (if id
                    (::opinion/value (d/pull db [[::opinion/value :default 0]] id))
                    0)]
    (cond
      (= old-value new-value) []                            ; do nothing
      (zero? new-value) (if id [[:db/retractEntity id]] []) ; retract, if in db

      id [[:db/add id ::opinion/value new-value]]           ; update if in db

      ; add to db
      :else [[:db/add (:db/id proposal) ::proposal/opinions "temp"]
             [:db/add (:db/id user) ::user/opinions "temp"]
             {:db/id "temp" ::opinion/value new-value}])))


(defn- ->neutralize-others
  "Transaction to retract all opinions of the `user` in a `process` except for `proposal`."
  [db user process proposal]
  (let [id (get-opinion-eid db user proposal)]
    (->>
      (d/q '[:find [?e ...]
             :in $ ?user ?process
             :where
             [?user ::user/opinions ?e]
             [?process ::process/proposals ?proposal]
             [?proposal ::proposal/opinions ?e]]
        db (:db/id user) (:db/id process))
      (remove #{id})
      (map #(vector :db/retractEntity %)))))

(defn ->set [db user process proposal value]
  [d.core/db? ::user/entity ::process/entity ::proposal/entity ::opinion/value => vector?]
  (concat
    (when (process/single-approve? process)
      (->neutralize-others db user process proposal))
    (->set-value db user proposal value)))

(defn get-values-for-proposal [db proposal-ident]
  (merge
    {1 0, -1 0}                                             ; default values
    (->> proposal-ident
      (d/pull db [{::proposal/opinions [::opinion/value]}])
      ::proposal/opinions
      (map :decide.models.opinion/value)
      frequencies)))