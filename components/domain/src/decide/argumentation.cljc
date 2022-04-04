(ns decide.argumentation
  (:require
    [#?(:default clojure.spec.alpha
        :cljs    cljs.spec.alpha) :as s]
    [com.fulcrologic.guardrails.core :refer [>def]]
    [decide.argument :as-alias argument]
    [decide.specs.common :as common]
    [decide.statement :as-alias statement]))

(>def ::argument/id uuid?)
(>def ::argument/type #{:pro :contra})
(>def ::argument/premise (s/keys :req [::statement/id]))

(>def ::statement/id uuid?)
(>def ::statement/content ::common/non-blank-string)