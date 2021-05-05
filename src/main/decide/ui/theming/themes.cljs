(ns decide.ui.theming.themes
  (:require [material-ui.styles :refer [create-mui-theme]]
            ["@material-ui/core/styles" :refer [responsiveFontSizes createMuiTheme]]
            [decide.ui.theming.styles :as styles]))

(def shared
  {:overrides
   {:MuiCardHeader
    {:root
     {:padding-bottom "4px"}}}})

(def light-theme
  {:palette
   {:type "light"
    :primary {:main styles/hhu-blue}
    :secondary {:main "#b3006b"}
    :success {:main "#008127"}}})

(def dark-theme
  {:palette
   {:type "dark"
    :primary {:main "#68b9e9"}
    :secondary {:main "#ea90bc"}
    :success {:main "#7fef97"}
    :background {:default "#121212"
                 :paper "#212121"}}
   :overrides
   {:MuiAppBar
    {:colorPrimary
     {:background-color "#212121fa"
      :color "#fff"}}}})

(def themes
  {:dark dark-theme
   :light light-theme})


(def get-mui-theme
  (memoize
    (fn [theme-key]
      (responsiveFontSizes
        (createMuiTheme
          (clj->js (get themes theme-key light-theme))
          (clj->js shared))))))