(ns decide.models.proposal-test
  (:require
   [clojure.spec.alpha :as s]
   [clojure.test :refer :all]
   [decide.models.process :as process]
   [decide.models.process.mutations :as process.api]
   [decide.models.proposal :as proposal]
   [decide.server-components.pathom3 :as pathom3]
   [decide.test-utils.common :refer [*conn* test-db-fixture]]
   [fulcro-spec.check :as _]
   [fulcro-spec.core :refer [=check=> assertions behavior]])
  (:import (java.util UUID)))

(use-fixtures :each test-db-fixture)

(deftest proposal-integration-test
  (let [parser (pathom3/make-processor {} *conn*)]
    (behavior "Someone logged in"
      (let [parser-existing-user (partial parser {:ring/request {:session {:id #uuid"0000fb5e-a9d0-44b6-b293-bb3c506fc0cb"}}})]
        (assertions

          "can add a new proposal to a process"
          (parser-existing-user
            [{(list `process.api/add-proposal
                {::proposal/id (UUID/randomUUID)
                 ::proposal/title "My first proposal"
                 ::proposal/body "This is a body!"
                 ::process/slug "test-decision"
                 ::process/proposals [{::proposal/id #uuid"5fdc8014-bd58-43f6-990f-00000000000a"}]})
              [::proposal/id]}])
          =check=>
          (_/embeds?*
            {`process.api/add-proposal
             (_/valid?* (s/keys :req [::proposal/id]))}))))))
