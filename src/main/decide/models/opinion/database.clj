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

(def rules
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
         [?opinion ::opinion/value 0]))]

    [(users-process-opinions ?user ?process ?opinion)
     [?user ::user/opinions ?opinion]
     [?proposal ::proposal/opinions ?opinion]
     [?process ::process/proposals ?proposal]]])


(>defn get-opinion [db user proposal]
  [d.core/db? ::user/entity ::proposal/entity => (s/nilable ::opinion/entity)]
  (when-let [opinion-id (d/q '[:find ?opinion .
                               :in $ ?user ?proposal
                               :where
                               [?user ::user/opinions ?opinion]
                               [?proposal ::proposal/opinions ?opinion]]
                          db (:db/id user) (:db/id proposal))]
    (d/entity db opinion-id)))

;; Thought Maybe make a tx function out if this?
(defn ->retract
  "Generate a transaction for retracting an opinion from the database. If the opinion is preferred between two opinions,
   the list of"
  [opinion]
  (let [next-opinion (:opinion/preferred-over opinion)
        previous-opinion (:opinion/_preferred-over opinion)]
    [(when (and previous-opinion next-opinion)
       [:db/add (:db/id previous-opinion) :opinion/preferred-over (:db/id next-opinion)])
     [:db.fn/retractEntity (:db/id opinion)]]))

(>defn- ->set-value
  "Generate a transaction to set `new-value` as an opinion of a user for a proposal. A value of `0` removes the opinion.
  DOES NOT VALIDATE ANYTHING!"
  [db user proposal new-value]
  [d.core/db? ::user/entity ::proposal/entity ::opinion/value => vector?]
  (if-let [opinion (get-opinion db user proposal)]
    (cond
      (= (::opinion/value opinion) new-value) []            ; do nothing
      (zero? new-value) (->retract opinion)
      :else [[:db/add (:db/id opinion) ::opinion/value new-value]]) ; update

    ; add new opinion
    (when-not (zero? new-value)
      [{:db/id "temp" ::opinion/value new-value}
       [:db/add (:db/id proposal) ::proposal/opinions "temp"]
       [:db/add (:db/id user) ::user/opinions "temp"]])))


(defn- ->neutralize-others
  "Transaction to retract all opinions of the `user` in a `process` except for `proposal`."
  [db user process proposal]
  (let [id (:db/id (get-opinion db user proposal))]
    (->>
      (d/q '[:find [?e ...]
             :in $ ?user ?process
             :where
             [?user ::user/opinions ?e]
             [?process ::process/proposals ?proposal]
             [?proposal ::proposal/opinions ?e]]
        db (:db/id user) (:db/id process))
      (remove #{id})
      (map #(vector :db.fn/retractEntity %)))))

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