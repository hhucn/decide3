(ns decide.models.process-test
  (:require
    [decide.server-components.pathom :as pathom]
    [decide.models.process :as process]
    [decide.models.user :as user]
    [clojure.test :refer [deftest is use-fixtures]]
    [fulcro-spec.core :refer [specification provided behavior assertions component provided! =>]]
    [decide.server-components.database :as database]
    [datahike.api :as d]
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

(use-fixtures :each db-fixture)

(deftest unauthorized-user-integration-test
  (let [parser (pathom/build-parser {} *conn*)]
    (component "Someone not authorized"
      (let [parser-without-session (partial parser {})]
        (assertions
          "can query for an existing process."
          (parser-without-session
            [{[::process/slug "test-decision"]
              [::process/slug ::process/title]}])
          =>
          {[::process/slug "test-decision"]
           #::process{:slug "test-decision"
                      :title "Meine Test-Entscheidung"}}


          "can not add a new process."
          (parser-without-session
            [{(list `process/add-process
                #::process{:slug "test"
                           :title "My Test-Title"
                           :description "foobar"})
              [::process/slug ::process/title ::process/description]}])
          =>
          {`process/add-process
           {:com.fulcrologic.rad.pathom/errors
            {:message "User is not logged in!"
             :data {}}}}

          "can not update an existing process."
          (parser-without-session
            [{`(process/update-process
                 #::process{:slug "test-decision"
                            :title "My NEW Test-Title"})
              [::process/title]}])
          =>
          {`process/update-process
           {:com.fulcrologic.rad.pathom/errors
            {:message "User is not logged in!"
             :data {}}}})))))

(deftest parser-integration-test
  (let [parser (pathom/build-parser {} *conn*)]
    (component "An authorized user"
      (let [parser-existing-user (partial parser {:ring/request {:session {:id #uuid"0000fb5e-a9d0-44b6-b293-bb3c506fc0cb"}}})]
        (assertions
          "can query for an existing process."
          (parser-existing-user
            [{[::process/slug "test-decision"]
              [::process/slug ::process/title]}])
          =>
          {[::process/slug "test-decision"]
           #::process{:slug "test-decision"
                      :title "Meine Test-Entscheidung"}}

          "can add a new process."
          (parser-existing-user
            [{(list `process/add-process
                #::process{:slug "test"
                           :title "My Test-Title"
                           :description "foobar"})
              [::process/slug ::process/title ::process/description]}])
          => {`process/add-process
              #::process{:slug "test"
                         :title "My Test-Title"
                         :description "foobar"}}

          "can query for the new process."
          (parser-existing-user
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
          (parser-existing-user
            [{[::process/slug "test"]
              [::process/end-time]}])
          =>
          {[::process/slug "test"] {}}

          "can query for the new moderators, who is just the user who created the process."
          (parser-existing-user
            [{[::process/slug "test"]
              [{::process/moderators [::user/id]}]}])
          =>
          {[::process/slug "test"]
           {::process/moderators [{::user/id #uuid"0000fb5e-a9d0-44b6-b293-bb3c506fc0cb"}]}})

        (component "who is a moderator of the process"
          (let [parser-with-moderator parser-existing-user]
            (assertions
              "can update the process afterwards"
              (parser-with-moderator
                [{`(process/update-process
                     #::process{:slug "test"
                                :title "My NEW Test-Title"})
                  [::process/title]}])
              =>
              {`process/update-process
               {::process/title "My NEW Test-Title"}}

              "can't update a process that is not in use."
              (parser-existing-user
                [{`(process/update-process
                     #::process{:slug "i-do-not-exist"
                                :title "My NEW Test-Title"})
                  [::process/title]}])
              =>
              {`process/update-process
               {:com.fulcrologic.rad.pathom/errors
                {:message "Slug is not in use!"
                 :data {:slug "i-do-not-exist"}}}})))

        (component "who isn't a moderator of the process"
          (let [parser-with-non-moderator (partial parser {:ring/request {:session {:id #uuid"001e7a7e-3eb2-4226-b9ab-36dddcf64106"}}})]
            (assertions
              "can not update an existing process."
              (parser-with-non-moderator
                [{`(process/update-process
                     #::process{:slug "test"
                                :title "My NEW Test-Title"})
                  [::process/title]}])
              =>
              {`process/update-process
               {:com.fulcrologic.rad.pathom/errors
                {:message "User is not moderator of this process"
                 :data {::user/id #uuid"001e7a7e-3eb2-4226-b9ab-36dddcf64106"
                        ::process/slug "test"}}}})))))))
