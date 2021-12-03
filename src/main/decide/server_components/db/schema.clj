(ns decide.server-components.db.schema
  (:require
    [datahike.api :as d]
    [decide.models.argumentation :as argumentation]
    [decide.models.opinion :as opinion]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]
    [decide.server-components.db.migrate :as migrate]))

(def schema
  (into [] cat
    [user/schema
     process/schema
     proposal/schema
     opinion/schema
     argumentation/schema
     migrate/data-migrations-schema]))

(def migrations
  [{:id "Rename :db/txUser to :tx/by"
    :up
    (fn [db]
      (for [[tx user] (d/q '[:find ?e ?user :where [?e :db/txUser ?user]] db)]
        [:db/add tx :tx/by user]))}])
