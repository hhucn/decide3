(ns decide.server-components.database
  (:require
    [datahike.api :as d]
    [decide.models.argument :as argument]
    [decide.models.opinion :as opinion]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]
    [decide.server-components.config :refer [config]]
    [com.fulcrologic.guardrails.core :refer [>defn =>]]
    [mount.core :refer [defstate args]]
    [taoensso.timbre :as log]
    [datahike.core :as d.core]))

(def schema
  (into [] cat
    [user/schema
     process/schema
     proposal/schema
     opinion/schema
     argument/schema]))

(def dev-db
  (concat
    (for [name ["Björn" "Martin" "Christian" "Markus" "Jan" "Alexander" "Marc"]]
      (assoc (user/tx-map {::user/email name
                           ::user/password name})
        :db/id name))
    [{::process/slug "test-decision"
      ::process/title "Meine Test-Entscheidung"
      ::process/description "Wir müssen irgendwas für die Uni entscheiden."
      ::process/latest-id 5,
      ::process/proposals
      [{:db/id "Wasserspender"
        ::proposal/body "Wasser ist gesund für Studenten.",
        ::proposal/created #inst"2020-12-18T09:58:15.232-00:00",
        ::proposal/id #uuid"5fdc7d37-107b-4484-ab85-2911be84c39e",
        ::proposal/nice-id 1,
        ::proposal/original-author "Christian",
        ::proposal/title "Wir sollten einen Wasserspender aufstellen"}
       {:db/id "goldener Wasserspender"
        ::proposal/body "Wasser ist gesund für Studenten, aber wir sollten auch auf \"Qualität\" achten.",
        ::proposal/created #inst"2020-12-18T10:10:28.182-00:00",
        ::proposal/id #uuid"5fdc8014-bd58-43f6-990f-713741c81d9f",
        ::proposal/nice-id 2,
        ::proposal/original-author "Martin",
        ::proposal/parents ["Wasserspender"],
        ::proposal/arguments (argument/tx-map {::argument/content "Völlige Verschwendung!" :author "Markus" ::argument/type :pro})
        ::proposal/title "Wir sollten einen goldenen Wasserspender aufstellen"}
       (-> #::proposal{:title "Ein 3-D Drucker für DIY Projekte"
                       :body "Viele DIY Projekte lassen sich heutzutage gut mithilfe von Prototypen aus dem 3-D Drucker bewerkstelligen. Z.B. ein Gehäuse für den Raspberry PI."
                       :created #inst"2020-12-19T10:10:28.182-00:00"
                       :nice-id 3
                       :original-author "Jan"}
         proposal/tx-map (assoc :db/id "3d-drucker"))
       (-> #::proposal{:title "Man könnte einen Hackerspace im ZIM bauen."
                       :body "Möchte man hardware-nahe Projekt entwickeln, dann benötigt man häufig einiges an Arbeitsmaterial, wie z.B. eine Lötstation oder Multimeter. Es könnte im ZIM ein Raum geschaffen werden, wo so etwas vorhanden ist."
                       :nice-id 4
                       :created #inst"2020-12-19T15:10:28.182-00:00"
                       :original-author "Marc"}
         proposal/tx-map (assoc :db/id "hackerspace"))
       (-> #::proposal{:title "Man könnte einen Hackerspace inkl. 3-D Drucker einrichten."
                       :body "Möchte man hardware-nahe Projekt entwickeln, dann benötigt man häufig einiges an Arbeitsmaterial, wie z.B. eine Lötstation oder Multimeter. Es könnte im ZIM ein Raum geschaffen werden, wo so etwas vorhanden ist. \n\n Hier könnte auch ein 3-D Drucker aufgestellt werden. "
                       :nice-id 5
                       :created #inst"2020-12-20T15:10:28.182-00:00"
                       :parents ["3d-drucker" "hackerspace"]
                       :original-author "Markus"}
         proposal/tx-map (assoc :db/id "hackerspace+3d"))]}]
    (apply concat
      (for [[proposal users-who-agree]
            {"Wasserspender" ["Christian" "Martin" "Alexander" "Jan"]
             "goldener Wasserspender" ["Martin" "Alexander"]
             "3d-drucker" ["Martin" "Jan" "Marc" "Christian"]
             "hackerspace" ["Björn" "Jan" "Marc" "Christian" "Markus"]
             "hackerspace+3d" ["Björn" "Jan" "Marc" "Christian" "Markus"]}]
        (apply concat
          (for [user users-who-agree]
            [[:db/add proposal ::proposal/opinions (str proposal "+" user)]
             [:db/add user ::user/opinions (str proposal "+" user)]
             {:db/id (str proposal "+" user)
              ::opinion/value 1}]))))))

#_(defn test-database [config]
    (d/delete-database config)
    (d/create-database
      (assoc config :initial-tx schema))
    (d/connect config))

(>defn transact-schema! [conn]
  [d.core/conn? => map?]
  (d/transact conn schema))

(defn ensure-database! [db-config]
  (let [db-exists? (d/database-exists? db-config)]
    (log/info "Database exists?" db-exists?)
    (log/info "Create database connection with URI:" db-config)
    (when-not db-exists?
      (log/info "Database does not exist! Creating...")
      (d/create-database db-config))))


(defn test-database [db-config]
  (d/create-database)
  (let [conn (d/connect)]
    (transact-schema! conn)
    (d/transact conn dev-db)
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
      (log/info "Transacting schema...")
      (try
        (transact-schema! conn)
        (catch Exception e (println e)))
      conn))
  :stop
  (d/release conn))