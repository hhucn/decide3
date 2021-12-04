(ns decide.specs.argument
  (:require
    [#?(:default clojure.spec.alpha
        :cljs    cljs.spec.alpha)
     :as s]
    [#?(:default clojure.spec.gen.alpha
        :cljs    cljs.spec.gen.alpha)
     :as gen]
    [decide.argument :as argument]
    [decide.statement :as statement]
    [decide.specs.statement]))

(s/def ::argument/id uuid?)
(s/def ::argument/type #{:pro :contra})
(s/def ::argument/premise (s/keys :req [::statement/id]))