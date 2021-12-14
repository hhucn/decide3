(ns decide.user.password-specs
  (:require
    [clojure.spec.alpha :as s]
    [clojure.spec.gen.alpha :as gen]
    [decide.specs.common :as common]
    [buddy.hashers :as h]))

(def bcrypt+sha512-pattern #"^(bcrypt\+sha512)\$([a-zA-Z0-9./]{32})\$([4-9]|[12][0-9]|3[01])\$([a-zA-Z0-9./]+)$")

(def bcrypt+sha512-hash-gen
  (gen/fmap
    #(h/derive % {:alg :bcrypt+sha512})
    common/non-empty-string-alphanumeric))

(s/def :hash/bcrypt+sha512
  (s/with-gen
    (fn [pass]
      (assert (string? pass))
      (re-matches bcrypt+sha512-pattern pass))
    (constantly bcrypt+sha512-hash-gen)))



