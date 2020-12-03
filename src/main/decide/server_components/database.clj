(ns decide.server-components.database
  (:require
    [datahike.api :as d]
    [decide.models.argument :as argument]
    [decide.models.opinion :as opinion]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]
    [decide.server-components.config :refer [config]]
    [mount.core :refer [defstate args]]
    [taoensso.timbre :as log]))

(def schema
  (into [] cat
    [user/schema
     proposal/schema
     opinion/schema
     argument/schema]))

(defn test-database [config]
  (d/delete-database config)
  (d/create-database
    (assoc config :initial-tx schema))
  (d/connect config))

(defstate conn
  :start
  (let [db-config (:db config)
        _ (when (:db/reset? db-config)
            (log/info "Reset Database")
            (d/delete-database db-config))
        db-exists? (d/database-exists? db-config)]
    (log/info "Database exists?" db-exists?)
    (log/info "Create database connection with URI:" db-config)
    (when-not db-exists?
      (log/info "Database does not exist! Creating...")
      (d/create-database db-config))

    (log/info "Database exists. Connecting...")
    (let [conn (d/connect db-config)]
      (log/info "Transacting schema...")
      (try
        (d/transact conn schema)
        (catch Exception e (println e)))

      conn))
  :stop
  (d/release conn))