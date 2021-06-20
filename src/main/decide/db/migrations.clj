(ns decide.db.migrations
  (:require
    [datahike.api :as d]
    [decide.models.argumentation :as argumentation]))

(defn- find-old-arguments [db]
  (d/q
    '[:find [(pull ?e [*]) ...]
      :where
      [?e :decide.models.argument/id]]
    db))

(defn- migrate-to-new-argument-tx [{:decide.models.argument/keys [id author content type]}]
  (-> #:argument{:id id :type type}
    argumentation/make-argument
    (assoc :decide.models.argument/id id)
    (assoc :argument/premise
           (-> {:statement/content content}
             argumentation/make-statement
             (assoc :author (:db/id author))))))

(defn migrate-arguments! [conn]
  (let [old-arguments (find-old-arguments @conn)]
    (d/transact conn
      (mapv migrate-to-new-argument-tx old-arguments))))