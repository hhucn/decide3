(ns decide.user.specs
  (:require
    [#?(:default clojure.spec.alpha
        :cljs    cljs.spec.alpha) :as s]
    [com.fulcrologic.guardrails.core :refer [>def >defn >defn- => | ? <-]]
    [decide.specs.common :as common]
    #?(:clj [decide.user.password-specs])))

(>def :decide.user/id uuid?)
(>def :decide.user/email ::common/email)
#?(:clj (s/def :decide.user/encrypted-password :hash/bcrypt+sha512))
(>def :decide.user/plain-password ::common/non-blank-string)
(>def :decide.user/display-name (s/and string? #(< 0 (count %) 50)))
(>def :iso/ISO-639-3 #(>= 3 (count (name %))))
(>def :decide.user/language (s/and simple-keyword? :iso/ISO-639-3))