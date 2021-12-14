(ns decide.user
  (:require
    [#?(:default clojure.spec.alpha
        :cljs    cljs.spec.alpha) :as s]
    [com.fulcrologic.guardrails.core :refer [>def >defn >defn- => | ? <-]]
    #?(:clj [decide.user.password :as user.password])
    [decide.user.specs]))

;;; CLJ only API
#?(:clj (def hash-password user.password/encrypt-password))
#?(:clj (def password-valid? user.password/password-valid?))