(ns decide.ui.theming.themes
  (:require [material-ui.styles :refer [create-mui-theme]]
            ["@material-ui/core/styles" :refer [responsiveFontSizes]]
            [decide.ui.theming.styles :as styles]))

(def shared (js->clj (create-mui-theme {}) :keywordize-keys true))
(def light-theme (merge
                   shared
                   {:palette {:type "light"
                              :primary {:main styles/hhu-blue}
                              :secondary {:main "#b3006b"}}}))

(def dark-theme (merge
                  shared
                  {:palette {:type "dark"
                             :primary {:main "#68b9e9"}
                             :secondary {:main "#f261a7"}}}))

(defn responsive-font-theme [theme]
  (responsiveFontSizes (create-mui-theme theme)))

(def compiled-themes
  {:dark (responsive-font-theme dark-theme)
   :light (responsive-font-theme light-theme)})

(defn get-mui-theme [theme-key]
  (get compiled-themes theme-key (:light compiled-themes)))