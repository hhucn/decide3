(ns variance
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [datahike.api :as d]
   [decide.models.opinion :as opinion]
   [decide.models.process :as-alias process]
   [decide.models.proposal :as-alias proposal]
   [decide.models.proposal.database :as proposal.db]
   [decide.models.user :as-alias user]
   [decide.server-components.database :refer [conn]]))

(def db (d/db conn))
(def hdb (d/history db))

(def slug "decide-experiment2")
(def process (d/entity db [::process/slug slug]))

(defn opinion-change-txs
  "Returns all transaction ids where an opinion in the `process` has changed."
  [process]
  (d/q '[:find [?t ...]
         :in $ ?process
         :where
         [?process ::process/proposals ?proposal]
         [?proposal ::proposal/opinions ?opinion]
         [?opinion ::opinion/value _ ?t]]
    (d/history (d/entity-db process))
    (:db/id process)))

(defn get-proposal->approvers [process]
  (let [proposals (::process/proposals process)]
    (zipmap proposals (map proposal.db/approvers proposals))))

(defn proposal+approvers [process]
  (for [proposal (::process/proposals process)]
    [proposal
     (proposal.db/approvers proposal)]))

(defn select-most-approved [process]
  (loop [proposals (proposal+approvers process)
         result    {}]
    (if (empty? proposals)
      result
      (let [[best & r] (reverse (sort-by (comp count second) proposals))
            approvers (second best)]
        (recur
          (map (fn [[proposal as]] [proposal (set/difference as approvers)]) r)
          (into result [best]))))))

(defn as-of [db tx]
  (d/filter db
    (fn [_db datom]
      (<= (:tx datom) tx))))

(def spread
  (let [process            process
        txs                (sort (opinion-change-txs process))
        historic-processes (map
                             (fn [tx]
                               (-> db
                                 (as-of tx)
                                 (d/entity (:db/id process))))
                             txs)]
    (for [process historic-processes]
      (reverse (sort (map (comp count second) (select-most-approved process)))))))

(def spread2
  (let [process            process
        txs                (sort (opinion-change-txs process))
        historic-processes (map
                             (fn [tx]
                               (-> db
                                 (as-of tx)
                                 (d/entity (:db/id process))))
                             txs)]
    (for [process historic-processes]
      (->> process
        ::process/proposals
        (map #(vector "_" (proposal.db/approvers %)))
        (map (comp count second))
        sort reverse))))

(defn pad-right [seq n v]
  (take n (concat seq (repeat v))))

(defn rectanglify-spread [s]
  (let [line-length (reduce max (map count s))]
    (map #(pad-right % line-length 0) s)))

(defn ->csv [spread]
  (->> spread
    rectanglify-spread
    dedupe
    (map #(str (str/join \, %) "\n"))
    (reduce str)))

(comment
  (spit "spread.csv" (->csv spread2)))
