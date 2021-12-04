(ns decide.specs.opinion
  (:require
    [#?(:default clojure.spec.alpha
        :cljs    cljs.spec.alpha)
     :as s]
    [#?(:default clojure.spec.gen.alpha
        :cljs    cljs.spec.gen.alpha)
     :as gen]
    [decide.opinion :as opinion]))

(s/def ::opinion/value #{-1 0 +1 +2})