(ns decide.utils.breakpoint
  (:require
    [com.fulcrologic.guardrails.core :refer [>defn =>]]
    [cljs.spec.alpha :as s]
    ["@material-ui/core/styles" :refer [useTheme]]
    [material-ui.utils :as utils]))

(s/def ::breakpoint #{"xs" "sm" "md" "lg" "xl"})

(>defn <=?
  "Checks if the current width of the viewport is equal or below the breakpoint."
  [bp]
  [::breakpoint => boolean?]
  (-> (useTheme) .-breakpoints (.down bp) utils/use-media-query))

(>defn >=?
  "Checks if the current width of the viewport is equal or above the breakpoint."
  [bp]
  [::breakpoint => boolean?]
  (-> (useTheme) .-breakpoints (.up bp) utils/use-media-query))

(>defn =?
  "Checks if the current width of the viewport is equal to the breakpoint."
  [bp]
  [::breakpoint => boolean?]
  (-> (useTheme) .-breakpoints (.only bp) utils/use-media-query))

(>defn between?
  "Checks if the current width of the viewport is between the breakpoints."
  [down-breakpoint up-breakpoint]
  [::breakpoint ::breakpoint => boolean?]
  (-> (useTheme) .-breakpoints (.between down-breakpoint up-breakpoint) utils/use-media-query))