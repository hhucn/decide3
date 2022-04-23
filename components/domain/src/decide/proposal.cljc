(ns decide.proposal
  (:require
    [#?(:default clojure.spec.alpha
        :cljs    cljs.spec.alpha) :as s]
    [com.fulcrologic.guardrails.core :refer [>def]]
    [decide.specs.common :as common]))

(def title-char-limit 80)

(>def ::id uuid?)
(>def ::nice-id pos-int?)
(>def ::title (s/and ::common/non-blank-string (common/limited-str {:max-length title-char-limit})))
(>def ::body string?)
(>def ::created inst?)
(>def ::parents (s/coll-of (s/keys :req [::id]) :distinct true))

(>def ::pro-votes nat-int?)
(>def ::con-votes nat-int?)
(>def ::no-of-parents nat-int?)
(>def ::no-of-children nat-int?)
(>def ::generation nat-int?)
