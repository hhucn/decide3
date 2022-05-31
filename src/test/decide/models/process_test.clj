(ns decide.models.process-test
  (:require
   [clojure.test :refer [deftest use-fixtures]]
   [decide.models.process :as process]
   [decide.models.process.mutations :as process.mutations]
   [decide.models.user :as user]
   [decide.server-components.pathom3 :as pathom3]
   [decide.test-utils.common :refer [*conn* test-db-fixture]]
   [fulcro-spec.check :as _]
   [fulcro-spec.core :refer [=> =check=> assertions behavior component specification]]))

(use-fixtures :each test-db-fixture)

(deftest unauthorized-user-integration-test
  (let [parser (pathom3/make-processor {} *conn*)]
    (component "Someone not authorized"
      (let [parser-without-session #(parser {:ring/request {}} %)]
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
            [{(list `process.mutations/add-process
                #::process{:slug "test"
                           :title "My Test-Title"
                           :description "foobar"})
              [::process/slug ::process/title ::process/description]}])
          =>
          {`process.mutations/add-process
           {:com.fulcrologic.rad.pathom/errors
            {:message "User is not logged in!"
             :data {}}}}

          "can not update an existing process."
          (parser-without-session
            [{`(process.mutations/update-process
                 #::process{:slug "test-decision"
                            :title "My NEW Test-Title"})
              [::process/title]}])
          =>
          {`process.mutations/update-process
           {:com.fulcrologic.rad.pathom/errors
            {:message "User is not logged in!"
             :data {}}}})))))

(deftest parser-integration-test
  (let [parser (pathom3/make-processor {} *conn*)]
    (behavior "An authorized user"
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
            [{(list `process.mutations/add-process
                #::process{:slug "test"
                           :title "My Test-Title"
                           :description "foobar"})
              [::process/slug ::process/title ::process/description]}])
          => {`process.mutations/add-process
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
                [{`(process.mutations/update-process
                     #::process{:slug "test"
                                :title "My NEW Test-Title"})
                  [::process/title]}])
              =>
              {`process.mutations/update-process
               {::process/title "My NEW Test-Title"}}

              "can not update a process that is not in use."
              (parser-existing-user
                [{`(process.mutations/update-process
                     #::process{:slug "i-do-not-exist"
                                :title "My NEW Test-Title"})
                  [::process/title]}])
              =>
              {`process.mutations/update-process
               {:com.fulcrologic.rad.pathom/errors
                {:message "Slug is not in use!"
                 :data {:slug "i-do-not-exist"}}}}

              "can add an end-date afterwards"
              (parser-with-moderator
                [{`(process.mutations/update-process
                     #::process{:slug "test" :end-time #inst"2030"})
                  [::process/end-time]}])
              =>
              {`process.mutations/update-process
               {::process/end-time #inst"2030"}}

              "can remove an end-date afterwards"
              (parser-with-moderator
                [{`(process.mutations/update-process
                     #::process{:slug "test" :end-time nil})
                  [::process/slug ::process/end-time]}])
              =>
              {`process.mutations/update-process
               #::process{:slug "test"}}

              "can not remove an required attributes"
              (get-in (parser-with-moderator
                        [{`(process.mutations/update-process
                             #::process{:slug "test" :title nil})
                          [::process/slug ::process/end-time]}])
                [`process.mutations/update-process :com.fulcrologic.rad.pathom/errors :message])
              =>
              "Failed validation!"

              "can not remove an required attributes"
              (parser-with-moderator
                [{`(process.mutations/update-process
                     #::process{:slug "test" :title nil})
                  [::process/slug ::process/end-time]}])
              =check=>
              (_/embeds?*
                {`process.mutations/update-process
                 {:com.fulcrologic.rad.pathom/errors
                  {:message "Failed validation!"}}})

              "can not remove slug"
              (parser-with-moderator
                [{`(process.mutations/update-process
                     #::process{:slug nil})
                  [::process/slug ::process/end-time]}])
              =check=>
              (_/embeds?*
                {`process.mutations/update-process
                 {:com.fulcrologic.rad.pathom/errors
                  {:message "Failed validation!"}}})

              "can not set title to empty"
              (parser-with-moderator
                [{`(process.mutations/update-process
                     #::process{:slug "test" :title ""})
                  [::process/slug ::process/end-time]}])
              =check=>
              (_/embeds?*
                {`process.mutations/update-process
                 {:com.fulcrologic.rad.pathom/errors
                  {:message "Failed validation!"}}}))))

        (component "who is not a moderator of the process"
          (let [parser-with-non-moderator (partial parser {:ring/request {:session {:id #uuid"001e7a7e-3eb2-4226-b9ab-36dddcf64106"}}})]
            (assertions
              "can not update the process."
              (parser-with-non-moderator
                [{`(process.mutations/update-process
                     #::process{:slug "test"
                                :title "My NEW Test-Title"})
                  [::process/title]}])
              =>
              {`process.mutations/update-process
               {:com.fulcrologic.rad.pathom/errors
                {:message "Need moderation role for this operation"
                 :data {::user/id #uuid"001e7a7e-3eb2-4226-b9ab-36dddcf64106"
                        ::process/slug "test"}}}})))))))

(specification "Malformed add-process parameters"
  (let [parser               (pathom3/make-processor {} *conn*)
        parser-existing-user (partial parser {:ring/request {:session {:id #uuid"0000fb5e-a9d0-44b6-b293-bb3c506fc0cb"}}})]
    (behavior "process can not be added with"
      (assertions

        "malformed slug"
        (parser-existing-user
          [{`(process.mutations/add-process
               #::process{:slug "I AM NOT A CORRECT SLUG"
                          :title "My Test-Title"
                          :description "foobar"})
            [::process/slug ::process/title ::process/description]}])
        =check=>
        (_/embeds?*
          {`process.mutations/add-process
           {:com.fulcrologic.rad.pathom/errors
            {:message "Failed validation!"}}})

        "empty title"
        (parser-existing-user
          [{(list `process.mutations/add-process
              #::process{:slug "correct-slug"
                         :title ""
                         :description "foobar"})
            [::process/slug ::process/title ::process/description]}])
        =check=>
        (_/embeds?*
          {`process.mutations/add-process
           {:com.fulcrologic.rad.pathom/errors
            {:message "Failed validation!"}}})

        "missing field"
        (parser-existing-user
          [{(list `process.mutations/add-process
              #::process{:slug "correct-slug"
                         :description "foobar"})
            [::process/slug ::process/title ::process/description]}])
        =check=>
        (_/embeds?*
          {`process.mutations/add-process
           {:com.fulcrologic.rad.pathom/errors
            {:message "Failed validation!"}}})

        "string as end-date"
        (parser-existing-user
          [{(list `process.mutations/add-process
              #::process{:slug "correct-slug"
                         :title "Bla"
                         :description "foobar"
                         :end-time "heute"})
            [::process/slug ::process/title ::process/description]}])
        =check=>
        (_/embeds?*
          {`process.mutations/add-process
           {:com.fulcrologic.rad.pathom/errors
            {:message "Failed validation!"}}})

        "slug is already in use"
        (parser-existing-user
          [{(list `process.mutations/add-process
              #::process{:slug "test-decision"
                         :title "Bla"
                         :description "foobar"})
            [::process/slug ::process/title ::process/description]}])
        =check=>
        (_/embeds?*
          {`process.mutations/add-process
           {:com.fulcrologic.rad.pathom/errors
            {:message "Slug already in use"}}})))))

(specification "Moderator privileges"
  (let [parser                   (pathom3/make-processor {} *conn*)
        parser-with-alex-session (partial parser {:ring/request {:session {:id #uuid"000aa0e2-e4d6-463d-ae7c-46765e13a31b"}}})]
    (behavior "Only a moderator can"
      (behavior "add new moderators"
        (assertions
          (parser-with-alex-session
            [`(process.mutations/add-moderator
                {::process/slug "test-decision"
                 ::user/email "Marc"})])
          =check=>
          (_/embeds?*
            {`process.mutations/add-moderator
             {:com.fulcrologic.rad.pathom/errors
              {:message "Need moderation role for this operation"}}}))))))

(specification "Private processes"
  (let [parser                   (pathom3/make-processor {} *conn*)
        parser-with-alex-session (partial parser {:ring/request {:session {:id #uuid"000aa0e2-e4d6-463d-ae7c-46765e13a31b"}}})]
    (behavior "A private process"
      (behavior "can not be queried by everyone"
        (assertions
          (parser-with-alex-session
            [{:root/all-processes [::process/slug]}])
          =>
          #:root{:all-processes [#:decide.models.process{:slug "test-decision"}]})))))