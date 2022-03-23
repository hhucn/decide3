(ns decide.models.opinion.database-test
  (:require [clojure.test :refer :all]
            [decide.models.opinion :as opinion]
            [decide.models.opinion.database :as opinion.db]))

(deftest ->remove-test
  (is (= [[:db.fn/retractEntity 42]] (opinion.db/->remove {:db/id 42}))))


