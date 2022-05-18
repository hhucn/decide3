(ns decide.database.interface
  (:require
   [datahike.api :as d]
   [datahike.core :as d.core]
   [decide.database.migrate :as migrate]
   [taoensso.encore :refer [defalias]]
   [taoensso.timbre :as log]))

(defalias transact d/transact)
(defalias squuid d.core/squuid)
(defalias pull d/pull)
(defalias pull-many d/pull-many)
(defalias db d/db)
(defalias since d/since)
(defalias db? d.core/db?)
(defalias conn? d.core/conn?)
(defalias q d/q)
(defalias entity d/entity)
(defalias entity-db d/entity-db)

(defn upsert-schema! [conn schema]
  (log/info "Upsert schema!")
  (migrate/upsert! conn schema))

(defn apply-migrations! [conn migrations]
  (migrate/migrate-data! conn migrations))

(defn transact-as
  [conn user-or-id arg-map]
  (let [user-id
        (or
          (:db/id user-or-id)
          [:decide.models.user/id
           (if (uuid? user-or-id)
             user-or-id
             (or
               (:decide.models.user/id user-or-id)
               (:decide.user/id user-or-id)))])]
    (transact conn
      (update arg-map :tx-data conj [:db/add "datomic.tx" :tx/by [:decide.models.user/id user-id]]))))

(defn ensure-database!
  "Creates a database only if it doesn't exist already."
  [config]
  (when-not (d/database-exists? config)
    (log/info "Database does not exist! Creating...")
    (d/create-database config)))

