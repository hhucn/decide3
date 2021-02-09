(ns decide.models.process-test
  (:require
    [decide.server-components.pathom :as pathom]
    [decide.models.process :as process]
    [decide.models.user :as user]
    [clojure.test :refer [deftest is use-fixtures]]
    [fulcro-spec.core :refer [specification provided behavior assertions component provided! =>]]
    [decide.server-components.database :refer [test-database]]
    [datahike.api :as d]
    [taoensso.timbre :as log]))

(def ^:dynamic *conn* nil)

(defn db-fixture [f]
  (binding [*conn* (log/with-level :info (test-database {}))]
    (f)
    (d/release *conn*)
    (d/delete-database)))

(use-fixtures :each db-fixture)

(deftest parser-integration-test
  (component "The pathom parser for the server"
    (let [parser (pathom/build-parser {} *conn*)
          parser-with-request (partial parser {:ring/request {:session {:id #uuid"0000fb5e-a9d0-44b6-b293-bb3c506fc0cb"}}})]
      (assertions
        "can add a new process."
        (parser-with-request
          `[{(process/add-process
               #::process{:slug "test"
                          :title "My Test-Title"
                          :description "foobar"})
             [::process/slug ::process/title ::process/description]}])
        => `{process/add-process
             #::process{:slug "test"
                        :title "My Test-Title"
                        :description "foobar"}}

        "can query for the new process."
        (parser-with-request
          [{[::process/slug "test"]
            [::process/slug ::process/title ::process/description ::process/end-time
             {::process/moderators [::user/id]}]}])
        =>
        {[::process/slug "test"]
         #::process{:slug "test"
                    :title "My Test-Title"
                    :description "foobar"
                    ::process/moderators [{::user/id #uuid"0000fb5e-a9d0-44b6-b293-bb3c506fc0cb"}]}}

        "can't get an end-time, since it wasn't specified on creation."
        (parser-with-request
          [{[::process/slug "test"]
            [::process/end-time]}])
        =>
        {[::process/slug "test"] {}}

        "can query for the new moderators, which is the user who created the process."
        (parser-with-request
          [{[::process/slug "test"]
            [{::process/moderators [::user/id]}]}])
        =>
        {[::process/slug "test"]
         {::process/moderators [{::user/id #uuid"0000fb5e-a9d0-44b6-b293-bb3c506fc0cb"}]}}))))
