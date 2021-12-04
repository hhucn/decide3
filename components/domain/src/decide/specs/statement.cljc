(ns decide.specs.statement
  (:require
    [#?(:default clojure.spec.alpha
        :cljs    cljs.spec.alpha)
     :as s]
    [#?(:default clojure.spec.gen.alpha
        :cljs    cljs.spec.gen.alpha)
     :as gen]
    [decide.statement :as statement]
    [decide.specs.common :as common]))

(s/def ::statement/id uuid?)
(s/def ::statement/content ::common/non-blank-string)