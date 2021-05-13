(ns decide.models.proposal-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer :all]
    [decide.models.process :as process]
    [decide.models.process.mutations :as process.api]
    [decide.models.proposal :as proposal]
    [decide.server-components.pathom :as pathom]
    [decide.test-utils.common :refer [db-fixture *conn*]]
    [fulcro-spec.check :as _]
    [fulcro-spec.core :refer [specification provided behavior assertions component provided! => =fn=> =check=>]])
  (:import (java.util UUID)))

(use-fixtures :each db-fixture)

(deftest proposal-integration-test
  (let [parser (pathom/build-parser {} *conn*)]
    (behavior "Someone logged in"
      (let [parser-existing-user (partial parser {:ring/request {:session {:id #uuid"0000fb5e-a9d0-44b6-b293-bb3c506fc0cb"}}})]
        (assertions

          "can add a new proposal to a process"
          (parser-existing-user
            [{(list `process.api/add-proposal
                {::proposal/id (UUID/randomUUID)
                 ::proposal/title "My first proposal"
                 ::proposal/body "This is a body!"
                 ::process/slug "test-decision"})
              [::proposal/id]}])
          =check=>
          (_/embeds?*
            {`process.api/add-proposal
             (_/valid?* (s/keys :req [::proposal/id]))}))))))