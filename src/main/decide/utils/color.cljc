(ns decide.utils.color
  (:require
    [com.fulcrologic.guardrails.core :refer [>defn => ? <-]]))

(def rgb-hex-pattern #"#[0-9A-Fa-f]{6}")

(defn hash-color
  "Return a #12DE32 hex color for a given string."
  [s]
  [any? => #(re-matches rgb-hex-pattern %)]
  (let [color-number (-> s hash (bit-and 0xFFFFFF))]
    #?(:cljs (str "#" (-> color-number (.toString 16) (.padStart 6 "0")))
       :clj (format "#%06x" color-number))))

