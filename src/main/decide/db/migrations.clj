(ns decide.db.migrations
  (:require
    [datahike.api :as d]
    [decide.models.argument :as argument]
    [decide.models.argumentation :as argumentation]
    [decide.server-components.database :refer [conn]]))

(defn- find-old-arguments [db]
  (d/q
    '[:find [(pull ?e [*]) ...]
      :where
      [?e ::argument/id]]
    db))

(defn- migrate-to-new-argument-tx [{::argument/keys [id author content type]}]
  (-> #:argument{:id id :type type}
    argumentation/make-argument
    (assoc ::argument/id id)
    (assoc :argument/premise
           (-> {:statement/content content}
             argumentation/make-statement
             (assoc :author (:db/id author))))))

(defn migrate-arguments! [conn]
  (let [old-arguments (find-old-arguments @conn)]
    (d/transact conn
      (mapv migrate-to-new-argument-tx old-arguments))))