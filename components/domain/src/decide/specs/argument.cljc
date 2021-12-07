(ns decide.specs.argument
  (:require
    [#?(:default clojure.spec.alpha
        :cljs    cljs.spec.alpha)
     :as s]))

(s/def :decide.argument/id uuid?)
(s/def :decide.argument/type #{:pro :contra})
(s/def :decide.argument/premise (s/keys :req [:decide.statement/id]))