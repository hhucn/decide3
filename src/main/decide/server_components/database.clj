(ns decide.server-components.database
  (:require
    [clojure.string :as str]
    [datahike.api :as d]
    [decide.models.user :as user]
    [decide.server-components.config :refer [config]]
    [com.fulcrologic.guardrails.core :refer [>defn =>]]
    [mount.core :refer [defstate args]]
    [taoensso.timbre :as log]
    [datahike.core :as d.core]
    [decide.server-components.db.migrate :as migrate]
    [decide.server-components.db.schema :as schema]))


(defn transact-as
  [conn user-or-id arg-map]
  [d.core/conn? any? map?]
  (let [user-id (if (uuid? user-or-id) user-or-id (:decide.models.user/id user-or-id))]
    (d/transact conn
      (update arg-map :tx-data conj [:db/add "datomic.tx" :tx/by [::user/id user-id]]))))

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

(>defn transact-schema! [conn]
  [d.core/conn? => map?]
  (d/transact conn schema/schema))

(defn ensure-database! [db-config]
  (when-not (d/database-exists? db-config)
    (log/info "Database does not exist! Creating...")
    (d/create-database db-config)))

(defn test-database [initial-db]
  (d/create-database)
  (let [conn (d/connect)]
    (transact-schema! conn)
    (d/transact conn initial-db)
    conn))

(defstate conn
  :start
  (let [db-config (:db config)
        reset? (:db/reset? db-config)]
    (when reset?
      (log/info "Reset Database")
      (d/delete-database db-config))

    (ensure-database! db-config)

    (log/info "Database exists. Connecting...")
    (let [conn (d/connect db-config)]
      (try
        (migrate/upsert! conn schema/schema)
        (migrate/migrate-data! conn schema/migrations)
        (catch Exception e (println e)))
      conn))
  :stop
  (d/release conn))