(ns decide.utils.header
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [com.fulcrologic.guardrails.core :refer [>defn >defn- => |]]))

(>defn- parse-single-lang [s]
  [string? => (s/tuple (s/coll-of string? :min-count 1)
                       (s/and number? #(<= 0 %)))]
  (let [[identifier weight] (str/split s #";q=")
        weight (cond
                 (= identifier "*") 0
                 weight (try (Float/parseFloat weight) (catch NumberFormatException _ 0))
                 :else 1)
        identifier (str/split identifier #"-")]
    [identifier weight]))

(defn preferred-language [header]
  [string? => string? | #(str/includes? header %)]
  (-> (if (str/blank? header) "*" header)
    (str/replace #"\s" "")
    (str/split #",")
    (->>
      (map parse-single-lang)
      (sort-by second >)
      ffirst
      first)))