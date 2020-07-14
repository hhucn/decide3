(ns decide.utils
  (:require
    ["@material-ui/core/styles" :refer (useTheme)]
    [material-ui.utils :as utils]))

(defn <=-breakpoint?
  "Checks if the current width of the viewport is equal or below the breakpoint."
  [bp]
  (-> (useTheme) .-breakpoints (.down bp) utils/use-media-query))

(defn >=-breakpoint?
  "Checks if the current width of the viewport is equal or above the breakpoint."
  [bp]
  (-> (useTheme) .-breakpoints (.up bp) utils/use-media-query))