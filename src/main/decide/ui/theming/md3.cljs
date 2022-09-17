(ns decide.ui.theming.md3
  (:require
   ["@material/material-color-utilities" :refer [themeFromSourceColor argbFromHex hexFromArgb]]
   [clojure.string :as str]))


(def hex->argb argbFromHex)
(def argb->hex hexFromArgb)

(defn make-custom-color [name source blend?]
  {:name name
   :value (argbFromHex source)
   :blend blend?})

(defn make-theme
  ([source-color]
   (make-theme source-color []))
  ([source-color custom-colors]
   (-> source-color
     hex->argb
     (themeFromSourceColor (clj->js custom-colors))
     (js->clj {:keywordize-keys true}))))


(defn scheme [theme mode]
  (-> theme
    (get-in [:schemes mode])
    (.toJSON)
    (js->clj {:keywordize-keys true})
    (update-vals argb->hex)))


(def color-keys
  #{:primary :secondary :tertiary :error
    :primaryContainer :secondaryContainer :tertiaryContainer :errorContainer

    :background
    :surface :surfaceVariant
    :inverseSurface :inversePrimary})


(defn- capitalize-first-letter [s]
  (str/join (cons (str/capitalize (first s)) (rest s))))

(defn- on-key [color-key]
  (if (= :inverseSurface color-key)
    :inverseOnSurface
    (keyword (str "on" (capitalize-first-letter (name color-key))))))


(defn mui-color [scheme color-key]
  {color-key
   (if-some [contrastColor (get scheme (on-key color-key))]
     {:main (get scheme color-key)
      :contrastText contrastColor}
     {:main (get scheme color-key)})})


(defn mui-custom-color [md3-custom-color mode]
  (let [{:keys [color onColor
                colorContainer onColorContainer]}
        (get md3-custom-color mode)]
    {(keyword (get-in md3-custom-color [:color :name]))
     {:main (argb->hex color)
      :contrastText (argb->hex onColor)}
     (keyword (str (get-in md3-custom-color [:color :name]) "Container"))
     {:main (argb->hex colorContainer)
      :contrastText (argb->hex onColorContainer)}}))


(defn core-color-palette [theme mode]
  (transduce
    (map #(mui-color (scheme theme mode) %))
    merge
    color-keys))


(defn custom-color-palette [theme mode]
  (let [custom-colors (:customColors theme)]
    (transduce
      (map #(mui-custom-color % mode))
      merge
      custom-colors)))


(defn mui-palette [theme mode]
  (let [md3-scheme (scheme theme mode)]
    (merge
      (core-color-palette theme mode)
      (custom-color-palette theme mode)
      {:mode (name mode)
       :divider (:outlineVariant md3-scheme)
       :background {:default (:background md3-scheme)
                    :paper (:surface md3-scheme)}})))

(comment

  (def custom-colors
    [(make-custom-color "success" "#008127" true)
     (make-custom-color "warning" "#ffc56f" true)
     (make-custom-color "info" "#0288d1" true)])

  (mui-palette (make-theme "#0061A7" custom-colors) :light)

  :_)

