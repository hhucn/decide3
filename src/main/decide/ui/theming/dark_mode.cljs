(ns decide.ui.theming.dark-mode)

(def dark-mode-matcher
  (and
    (.-matchMedia js/window)
    (js/window.matchMedia "(prefers-color-scheme: dark)")))

(defn register-dark-mode-listener [f]
  (when dark-mode-matcher
    (.addListener dark-mode-matcher f)))

(defn dark-mode?
  "Checks for prefers-color-scheme: dark. (clj always returns false)"
  []
  (and dark-mode-matcher (.-matches dark-mode-matcher)))