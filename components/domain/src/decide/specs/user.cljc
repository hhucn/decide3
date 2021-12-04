(ns decide.specs.user
  (:require
    [#?(:default clojure.spec.alpha
        :cljs    cljs.spec.alpha) :as s]
    [clojure.string :as str]
    [decide.specs.common :as common]
    [decide.user :as user]))

(s/def ::user/id uuid?)
(s/def ::user/email ::common/email)
(s/def ::user/encrypted-password string?)
(s/def ::user/password
  (s/or
    :encrypted ::user/encrypted-password
    :plain string?))
(s/def ::user/display-name (s/and string? #(< 3 (count %) 50)))
(s/def :iso/ISO-639-3 #(>= 3 (count (name %))))
(s/def ::user/language (s/and simple-keyword? :iso/ISO-639-3))
