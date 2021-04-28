(ns decide.utils.time
  (:require
    [com.fulcrologic.guardrails.core :refer [>defn => | <- ?]])
  (:import (java.util Date)))

(>defn past? [date]
  [any? => boolean?]
  (neg? (compare date (Date.))))