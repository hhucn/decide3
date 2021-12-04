(ns decide.specs.proposal
  (:require
    [#?(:default clojure.spec.alpha
        :cljs    cljs.spec.alpha)
     :as s]
    [decide.proposal :as proposal]
    [decide.specs.common :as common]))

(s/def ::proposal/id uuid?)
(s/def ::proposal/nice-id pos-int?)
(s/def ::proposal/title ::common/non-blank-string)
(s/def ::proposal/body string?)
(s/def ::proposal/created inst?)
(s/def ::proposal/pro-votes nat-int?)
(s/def ::proposal/con-votes nat-int?)
(s/def ::proposal/parents (s/coll-of (s/keys :req [::proposal/id]) :distinct true))
