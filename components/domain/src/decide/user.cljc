(ns decide.user
  (:require
    [buddy.hashers :as hs]
    [com.fulcrologic.guardrails.core :refer [>defn >defn- => | ? <-]]))

(>defn hash-password [password]
  [::password => ::encrypted-password]
  (hs/derive password {:alg :bcrypt+sha512}))

(>defn password-valid? [attempt encrypted-password]
  [::encrypted-password ::password => boolean?]
  (hs/check attempt encrypted-password))