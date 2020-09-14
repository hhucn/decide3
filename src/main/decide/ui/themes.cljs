(ns decide.ui.themes
  (:require [material-ui.styles :refer [create-mui-theme]]
            [decide.ui.styles :as styles]
            [taoensso.timbre :as log]))

(def shared (js->clj (create-mui-theme {}) :keywordize-keys true))
(def light-theme (merge
                   shared
                   {:palette {:type    "light"
                              :primary {:main styles/hhu-blue}
                              :secondary {:main "#b3006b"}}}))

(def dark-theme (merge
                  shared
                  {:palette {:type    "dark"
                             :primary {:main "#48a9e6"}
                             :secondary {:main "#b3006b"}}}))

(def compiled-themes
  {:dark  (create-mui-theme dark-theme)
   :light (create-mui-theme light-theme)})

(defn get-mui-theme [theme-key]
  (get compiled-themes theme-key (:light compiled-themes)))