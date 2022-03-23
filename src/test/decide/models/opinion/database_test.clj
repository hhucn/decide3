(ns decide.models.opinion.database-test
  (:require [clojure.test :refer :all]
            [decide.models.opinion :as opinion]
            [decide.models.opinion.database :as opinion.db]))

(deftest ->remove-test
  (is (= [[:db.fn/retractEntity 42]] (opinion.db/->remove {:db/id 42}))))



(deftest ->un-favorite-test
  (is (= [[:db/add 42 ::opinion/value 1]] (opinion.db/->un-favorite {:db/id 42
                                                                     ::opinion/value 2})))
  (is (= [] (opinion.db/->un-favorite {:db/id 42
                                       ::opinion/value 1}))
    "A non-favorite opinion shouldn't be un-favorit-ed."))


(deftest ->update-test
  (let [old-opinion {:db/id 42, ::opinion/value 1}]
    (is (= [[:db/add 42 ::opinion/value 2]] (opinion.db/->update old-opinion {:db/id 42, ::opinion/value 2})))
    (is (= [] (opinion.db/->update old-opinion old-opinion))
      "Updating without new data should be a no-op")
    (is (= [[:db/add 42 ::opinion/value 0]] (opinion.db/->update old-opinion {:db/id 42, ::opinion/value 0}))
      "Zero values should not be retracted.")))

