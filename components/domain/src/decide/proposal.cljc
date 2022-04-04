(ns decide.proposal
  (:require
    [#?(:default clojure.spec.alpha
        :cljs    cljs.spec.alpha) :as s]
    [com.fulcrologic.guardrails.core :refer [>def]]
    [decide.specs.common :as common]))


(>def ::id uuid?)
(>def ::nice-id pos-int?)
(>def ::title ::common/non-blank-string)
(>def ::body string?)
(>def ::created inst?)
(>def ::parents (s/coll-of (s/keys :req [::id]) :distinct true))

(>def ::pro-votes nat-int?)
(>def ::con-votes nat-int?)
