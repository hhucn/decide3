(ns decide.utils.slugify
  (:require
    [#?(:default clojure.spec.alpha
        :cljs    cljs.spec.alpha) :as s]
    [clojure.string :as str]
    [com.fulcrologic.guardrails.core :refer [>def >defn >defn- => | ? <-]]
    [decide.specs.common :as common]))

(def slug-pattern #"^[a-z0-9]+(?:[-_][a-z0-9]+)*$")
(>def ::slug (s/and string? #(re-matches slug-pattern %)))

(defn- keep-chars [s re]
  (apply str (re-seq re s)))

; TODO Move to util ns
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