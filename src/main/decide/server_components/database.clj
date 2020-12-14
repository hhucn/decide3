(ns decide.server-components.database
  (:require
    [datahike.api :as d]
    [decide.models.argument :as argument]
    [decide.models.opinion :as opinion]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]
    [decide.server-components.config :refer [config]]
    [mount.core :refer [defstate args]]
    [taoensso.timbre :as log]))

(def schema
  (into [] cat
    [user/schema
     process/schema
     proposal/schema
     opinion/schema
     argument/schema]))

(def dev-db
  [{::process/slug "test-decision"}])

(defn test-database [config]
  (d/delete-database config)
  (d/create-database
    (assoc config :initial-tx schema))
  (d/connect config))

(def orphaned-proposals
  '[:find [?e ...]
    :where
    [?e :decide.models.proposal/id]
    (not [_ :decide.models.process/proposals ?e])])

(defn add-orphan-proposals-to-process [conn]
  (let [es (d/q orphaned-proposals @conn)]
    (d/transact conn
      (mapv #(vector :db/add [::process/slug "test-decision"] ::process/proposals %) es))))

(defn transact-schema [conn]
  (d/transact conn schema))

(defstate conn
  :start
  (let [db-config (:db config)
        reset? (:db/reset? db-config)
        _ (when reset?
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
        (transact-schema conn)
        (when reset? (d/transact conn dev-db))
        (catch Exception e (println e)))

      conn))
  :stop
  (d/release conn))