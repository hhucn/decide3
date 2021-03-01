(ns decide.utils.time-test
  (:require
    [decide.utils.time :as time]
    [fulcro-spec.core :refer [specification provided behavior assertions component provided! =>]]))

(defn +days [^js/Date date days]
  (.setDate date (+ (.getDate date) days))
  date)

(specification "Time predicates"
  (behavior "work for `today?`"
    (assertions
      "Current time is today"
      (time/today? (js/Date.)) => true
      "yesterday is not today"
      (time/today? (+days (js/Date.) -1)) => false
      "tomorrow is not today"
      (time/today? (+days (js/Date.) +1)) => false))
  (behavior "work for `yesterday?`"
    (assertions
      "one day ago is yesterday"
      (time/yesterday? (+days (js/Date.) -1)) => true
      "two days ago is not yesterday"
      (time/yesterday? (+days (js/Date.) -2)) => false
      "tomorrow is not yesterday"
      (time/yesterday? (+days (js/Date.) 1)) => false)))