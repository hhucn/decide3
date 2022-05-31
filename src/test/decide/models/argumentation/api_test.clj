(ns decide.models.argumentation.api-test
  (:require [clojure.test :refer :all]
            [decide.models.argumentation.api :refer [add-argument-to-statement]]
            [decide.server-components.pathom3 :as pathom3]
            [decide.test-utils.common :refer [*conn* db-fixture]]))

(def test-db
  [{:db/id "Participating user" :decide.models.user/id #uuid"0000fb5e-a9d0-44b6-b293-bb3c506fc0cb"}
   {:db/id "Not participating user" :decide.models.user/id #uuid"0000fb5e-a9d0-44b6-b293-bb3c506f0000"}
   #:decide.models.process{:slug "private-process"
                           :type :decide.models.process/type.private
                           :participants ["Participating User"]
                           :proposals
                           [#:decide.models.proposal{:arguments
                                                     [#:argument{:id #uuid"0000fb5e-a9d0-44b6-b293-000000000000"
                                                                 :premise
                                                                 #:statement{:id #uuid"0000fb5e-a9d0-44b6-b293-bb3c00000000"}}]}]}])


(use-fixtures :each (db-fixture test-db))

(deftest add-argument-to-statement-test
  (let [parser (pathom3/make-processor {} *conn*)]
    (testing "Without login"
      (let [parser-without-user (partial parser {:ring/request {:session {}}})]
        (is (= {`add-argument-to-statement {:error "Needs login"}}
              (parser-without-user
                [(list `add-argument-to-statement {:statement {:statement/id 42}})]))
          "Login is needed to add an argument.")))


    (testing "With login"
      (testing "add argument to invalid statement"
        (let [parser-with-user   (partial parser {:ring/request {:session {:id #uuid"0000fb5e-a9d0-44b6-b293-bb3c506f0000"}}})
              invalid-conclusion {:statement/id #uuid"0000fb5e-a9d0-44b6-b293-bb3c002300d0"}
              valid-argument     #:argument{:id #uuid"0000fb5e-a9d0-44b6-b293-bb3c002345d0"
                                            :premise #:statement{:id #uuid"0000fb5e-a9d0-44b6-b293-bb3c00234567"
                                                                 :content "My new argument!"}}
              shape-result       #(-> % (get `add-argument-to-statement) (dissoc :tempids))
              result             (->
                                   [(list `add-argument-to-statement {:conclusion invalid-conclusion, :argument valid-argument})]
                                   parser-with-user
                                   shape-result)]

          (is (= #:com.wsscode.pathom.core{:errors [{:data {:conclusion invalid-conclusion}
                                                     :message "Conclusion not found"}]}
                result)))))))
