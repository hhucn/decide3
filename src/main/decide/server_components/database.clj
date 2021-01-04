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
  [(assoc (user/new {::user/email "Björn"
                     ::user/password "Björn"})
     :db/id "Björn")
   {::process/slug "test-decision"
    ::process/latest-id 2,
    ::process/proposals
    [{:db/id "Wasserspender"
      ::proposal/body "Wasser ist gesund für Studenten.",
      ::proposal/created #inst"2020-12-18T09:58:15.232-00:00",
      ::proposal/id #uuid"5fdc7d37-107b-4484-ab85-2911be84c39e",
      ::proposal/nice-id 1,
      ::proposal/original-author "Björn",
      ::proposal/title "Wir sollten einen Wasserspender aufstellen"}
     {::proposal/body "Wasser ist gesund für Studenten, aber wir sollten auch auf \"Qualität\" achten.",
      ::proposal/created #inst"2020-12-18T10:10:28.182-00:00",
      ::proposal/id #uuid"5fdc8014-bd58-43f6-990f-713741c81d9f",
      ::proposal/nice-id 2,
      ::proposal/original-author "Björn",
      ::proposal/parents ["Wasserspender"],
      ::proposal/title "Wir sollten einen goldenen Wasserspender aufstellen"}]}])

(defn test-database [config]
  (d/delete-database config)
  (d/create-database
    (assoc config :initial-tx schema))
  (d/connect config))

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