(ns decide.server-components.database
  (:require
   [clojure.string :as str]
   [datahike.api :as d]
   [decide.database.interface :as database]
   [decide.server-components.config :refer [config]]
   [decide.server-components.db.schema :as schema]
   [mount.core :refer [defstate]]
   [taoensso.timbre :as log]))

(defn ^:deprecated transact-as [conn user-or-id arg-map]
  (database/transact-as conn user-or-id arg-map))

(defn- empty-or-nil-field? [[_ v]]
  (or
    (nil? v)
    (and (string? v) (str/blank? v))))

(defn- retract-statement [eid-or-ident v]
  [:db/retract eid-or-ident v])

(defn retract-empty?-tx [eid-or-ident m]
  (->> m
    (filter empty-or-nil-field?)
    keys
    (mapv #(retract-statement eid-or-ident %))))

(defn test-database [initial-db]
  (d/create-database)
  (let [conn (d/connect)]
    (d/transact conn schema/schema)
    (d/transact conn initial-db)
    conn))

(defstate conn
  :start
  (let [db-config (:db config)
        reset? (:db/reset? db-config)]
    (when reset?
      (log/info "Reset Database")
      (d/delete-database db-config))

    (database/ensure-database! db-config)

    (log/info "Database exists. Connecting...")
    (let [conn (d/connect db-config)]
      (try
        (database/upsert-schema! conn schema/schema)
        (database/apply-migrations! conn schema/migrations)
        (catch Exception e (println e)))
      conn))
  :stop
  (d/release conn))