(ns decide.ui.components.dark-mode-toggle
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [mui.inputs.toggle-button :as toggle]
    ["@mui/icons-material/Brightness3" :default DarkModeIcon]
    ["@mui/icons-material/Brightness7" :default LightModeIcon]
    ["@mui/icons-material/BrightnessAuto" :default BrightnessAutoIcon]
    [com.fulcrologic.fulcro.dom :as dom]
    [decide.ui.storage :as storage]
    [mui.inputs.form :as form]
    [mui.inputs.input :as input]))

(defsc DarkModeToggle [this props]
  {:query [[storage/localstorage-key :theme]]
   :initial-state {}}
  (let [theme (get props [storage/localstorage-key :theme] "light")]
    (toggle/button-group
      {:value theme
       :exclusive true
       :fullWidth true
       :size :small
       :onChange
       (fn [e new-value]
         (when new-value
           (comp/transact! this
             [(if (= "light" new-value)
                (storage/remove-item {:key :theme})
                (storage/set-item {:key :theme, :value new-value}))])))}
      (toggle/button {:value "light"} (dom/create-element LightModeIcon))
      (toggle/button {:value "auto"} (dom/create-element BrightnessAutoIcon))
      (toggle/button {:value "dark"} (dom/create-element DarkModeIcon)))))

(def ui-dark-mode-toggle (comp/factory DarkModeToggle))