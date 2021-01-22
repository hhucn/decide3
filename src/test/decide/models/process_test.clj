(ns decide.models.process-test
  (:require
    [decide.server-components.pathom :as pathom]
    [decide.models.process :as process]
    [clojure.test :refer [deftest is use-fixtures]]
    [fulcro-spec.core :refer [specification provided behavior assertions component provided! =>]]
    [decide.server-components.database :refer [test-database]]
    [datahike.api :as d]))

(def ^:dynamic *conn* nil)

(defn db-fixture [f]
  (binding [*conn* (test-database {})]
    (f)
    (d/release *conn*)
    (d/delete-database)))

(use-fixtures :each db-fixture)

(deftest parser-integration-test
  (component "The pathom parser for the server"
    (let [parser (pathom/build-parser {} *conn*)]
      (assertions
        "can add a new process"
        (parser {:ring/request {:session #uuid"eeeeeeee-c47b-46df-b74d-161a04e65b7e"}}
          `[{(process/add-process
               #::process{:slug      "test"
                          :title "My Test-Title"
                          :description "foobar"})
             [::process/slug ::process/title ::process/description]}])
        => `{process/add-process
             #::process{:slug      "test"
                        :title "My Test-Title"
                        :description "foobar"}}))))