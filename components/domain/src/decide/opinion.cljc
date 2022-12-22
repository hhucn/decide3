(ns decide.opinion
  (:require
   [com.fulcrologic.guardrails.core :refer [>def]]))

(def reject -1)
(def neutral 0)
(def approval +1)
(def favorite +2)

(>def ::value #{-1 0 +1 +2})

(def approval-value? pos?)
(def neutral-value? zero?)
(def reject-value? neg?)
(defn favorite-value? [x] (= x 2))