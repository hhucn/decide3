(ns decide.process
  (:require
    [#?(:default clojure.spec.alpha
        :cljs    cljs.spec.alpha) :as s]
    [clojure.string :as str]
    [com.fulcrologic.guardrails.core :refer [>def >defn >defn- => | ? <-]]
    [decide.specs.process]
    [decide.specs.common :as common]))

(defn- keep-chars [s re]
  (apply str (re-seq re s)))

(>defn slugify
  "Returns a url-friendly slug version"
  [s]
  [::common/non-blank-string => ::slug]
  (-> s
    str/lower-case
    str/trim
    (str/replace #"[\s-]+" "-")                             ; replace multiple spaces and dashes with a single dash
    (keep-chars #"[a-z0-9-]")
    (str/replace #"^-|" "")))                               ; remove dash prefix
