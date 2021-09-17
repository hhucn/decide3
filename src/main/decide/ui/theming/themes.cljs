(ns decide.ui.theming.themes
  (:require
    [mui.styles :refer [create-mui-theme]]
    ["@mui/utils" :refer [deepmerge]]
    ["@mui/material/styles" :refer [responsiveFontSizes createTheme]]
    [decide.ui.theming.styles :as styles]
    [taoensso.timbre :as log]))

(def shared
  {:overrides
   {:MuiCardHeader
    {:root
     {:padding-bottom "4px"}}}})

(def light-theme
  {:palette
   {:mode "light"
    :primary {:main styles/hhu-blue}
    :secondary {:main "#b3006b"}
    :success {:main "#008127"}}})

(def dark-theme
  {:palette
   {:mode "dark"
    :background {:default "#121416", :paper "#101519"}
    :primary {:main "#68b9e9"}
    :secondary {:main "#ea90bc"}
    :success {:main "#7fef97"}}})

(def themes
  {:dark dark-theme
   :light light-theme})


(def get-mui-theme
  (memoize
    (fn [theme-key]
      (responsiveFontSizes
        (createTheme
          (deepmerge
            (clj->js shared)
            (clj->js (get themes theme-key light-theme))))))))