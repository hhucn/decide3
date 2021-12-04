(ns decide.user
  (:require
    [buddy.hashers :as hs]
    [#?(:default clojure.spec.alpha
        :cljs    cljs.spec.alpha) :as s]
    [com.fulcrologic.guardrails.core :refer [>def >defn >defn- => | ? <-]]
    [decide.specs.common :as common]))

(>def ::id uuid?)
(>def ::email ::common/email)
(>def ::encrypted-password string?)
(>def ::password
  (s/or
    :encrypted ::encrypted-password
    :plain string?))
(>def ::display-name (s/and string? #(< 0 (count %) 50)))
(>def :iso/ISO-639-3 #(>= 3 (count (name %))))
(>def ::language (s/and simple-keyword? :iso/ISO-639-3))

(>defn hash-password [password]
  [::password => ::encrypted-password]
  (hs/derive password {:alg :bcrypt+sha512}))

(>defn password-valid? [attempt encrypted-password]
  [::encrypted-password ::password => boolean?]
  (hs/check attempt encrypted-password))