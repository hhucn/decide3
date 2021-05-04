(ns decide.test-utils.common
  (:require
    [datahike.api :as d]
    [decide.server-components.database :as database]
    [taoensso.timbre :as log]))


(def ^:dynamic *conn* nil)

(defn db-fixture [f]
  (binding [*conn* (log/with-level :info (database/test-database database/dev-db))]
    (try
      (f)
      (catch Exception e (throw e))
      (finally
        (d/release *conn*)
        (d/delete-database)))))
