(ns decide.user.password
  (:require
    [buddy.hashers :as hs]
    [com.fulcrologic.guardrails.core :refer [>def >defn >defn- => | ? <-]]
    [decide.user.specs]))

(>defn encrypt-password [password]
  [:decide.user/plain-password => :decide.user/encrypted-password]
  (hs/derive password {:alg :bcrypt+sha512}))

(>defn password-valid? [encrypted-password password]
  [:decide.user/encrypted-password :decide.user/plain-password => boolean?]
  (try
    (hs/check password encrypted-password)
    (catch Exception _
      false)))

