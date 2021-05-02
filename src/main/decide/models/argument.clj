(ns decide.models.argument
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]))

(s/def ::id uuid?)
(s/def ::ident (s/tuple #{::id} ::id))
(s/def ::content (s/and string? (complement str/blank?)))
(s/def ::type #{:pro :contra})
