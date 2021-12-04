(ns decide.opinion)

(def approval-value? pos?)
(def neutral-value? zero?)
(def reject-value? neg?)
(defn favorite-value? [x] (= x 2))