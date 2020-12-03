(ns decide.models.argument
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]))

(def add-statement `add-statement)

(defsc Statement [_ _]
  {:query [::id ::content ::author]})