(ns decide.opinion
  (:require
    [com.fulcrologic.guardrails.core :refer [>def]]))

(>def ::value #{-1 0 +1 +2})

(def approval-value? pos?)
(def neutral-value? zero?)
(def reject-value? neg?)
(defn favorite-value? [x] (= x 2))