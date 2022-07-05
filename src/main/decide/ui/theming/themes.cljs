(ns decide.ui.theming.themes
  (:require
   ["@mui/material/styles" :refer [responsiveFontSizes createTheme]]
   ["@mui/utils" :refer [deepmerge]]
   ["@material/material-color-utilities" :refer [themeFromSourceColor argbFromHex hexFromArgb applyTheme]]))

(defn make-custom-color [name source blend?]
  {:name name
   :value (argbFromHex source)
   :blend blend?})

(defn make-md3-theme
  ([source-color]
   (make-md3-theme source-color []))
  ([source-color custom-colors]
   (-> source-color
     argbFromHex
     (themeFromSourceColor (clj->js custom-colors))
     (js->clj {:keywordize-keys true}))))


(defn scheme [theme key]
  (let [custom-colors (:customColors theme)])
  (-> theme
    (get-in [:schemes key])
    .toJSON
    (js->clj {:keywordize-keys true})
    (update-vals #(hexFromArgb %))))


(defn md3-scheme->mui-palette [scheme]
  (merge scheme
    (update-vals scheme #(array-map :main %))
    {:background {:default (:background scheme)
                  :paper (:surface scheme)}}))


(def custom-colors
  [(make-custom-color "success" "#008127" true)
   (make-custom-color "warning" "#ffc56f" true)
   (make-custom-color "info" "#0288d1" true)])

(defn custom-colors->palette [theme theme-key]
  (letfn [(palette-entry [color]
            {(get-in color [:color :name])
             {:main (hexFromArgb (get-in color [theme-key :color]))}})]
    (into {} (map palette-entry) (:customColors theme))))


(custom-colors->palette (make-md3-theme "#0061A7" custom-colors) :light)
(scheme (make-md3-theme "#0061A7" custom-colors) :light)

(defn- overlay [props]
  {"&:after"
   (merge
     {:position :absolute
      :pointerEvents :none
      :opacity 0
      :content "''"
      :top 0
      :left 0
      :width "100%"
      :height "100%"
      :transition "opacity 0.2s cubic-bezier(0.4, 0.0, 0.2, 1)"}
     props)})

(defn- overlay+hover [props]
  (merge {"&:hover:after" {:opacity 0.08}}
    (overlay props)))

(def elevation-opacity
  {0 0
   1 0.05
   2 0.08
   3 0.11
   4 0.12
   5 0.14})

(defn- with-elevation [props color elevation]
  (update props "&:after"
    merge
    {:position :absolute
     :pointerEvents :none
     :backgroundColor color
     :opacity (elevation-opacity elevation)
     :content "''"
     :top 0
     :left 0
     :width "100%"
     :height "100%"
     :transition "opacity 0.2s cubic-bezier(0.4, 0.0, 0.2, 1)"}))


(defn md3->mui-theme [theme theme-key]
  (let [scheme (scheme theme theme-key)]
    {:palette (merge
                (md3-scheme->mui-palette scheme)
                (custom-colors->palette theme theme-key)
                {:mode (name theme-key)})
     :components
     {:MuiFab
      {:variants
       [{:props {:color :tertiary}
         :style {:background (:tertiaryContainer scheme)
                 :color (:onTertiaryContainer scheme)
                 "&:hover" {:background (:tertiaryContainer scheme)}
                 "&:after" {:background (:onTertiaryContainer scheme)}}}]
       :styleOverrides
       {:root
        (merge
          {:borderRadius "16px"
           :overflow :hidden}
          (overlay+hover {}))
        :extended
        {:minWidth "80px"
         :height "56px"}}}

      :MuiInputBase
      {:styleOverrides
       {:root
        {:height "56px"}}}

      :MuiDialog
      {:styleOverrides
       {:paper
        (with-elevation
          {:borderRadius "24px"
           :backgroundColor (:surface scheme)
           :color (:onSurface scheme)
           :paddingTop "8px"
           :paddingBottom "8px"}
          (:primary scheme)
          4)}}

      :MuiDialogActions
      {:styleOverrides
       {:spacing
        {:paddingLeft "24px"
         :paddingRight "24px"
         :paddingBottom "16px"}}}

      :MuiButton
      {:styleOverrides
       {:root
        {:borderWidth "1px"
         :borderRadius "50rem"
         :paddingRight "24px"
         :paddingLeft "24px"
         :textTransform "none"}
        :startIcon {:marginLeft "-8px"}
        :endIcon {:marginRight "-8px"}}}

      :MuiAlert
      {:styleOverrides
       {:standardWarning
        {:backgroundColor (:tertiaryContainer scheme)
         :color (:onTertiaryContainer scheme)}}}

      :MuiToggleButtonGroup
      {:styleOverrides
       {:grouped
        {:borderRadius "100px"
         :minWidth "48px"
         :height "48px"
         :paddingRight "12px"
         :paddingLeft "12px"}}}

      :MuiAppBar
      {:variants
       [{:props {:variant "flat"}
         :style {:backgroundColor (:surface scheme)
                 :color (:onSurface scheme)}}
        {:props {:variant "on-scroll"}
         :style (with-elevation
                  {:backgroundColor (:surface scheme)
                   :color (:onSurface scheme)}
                  (:primary scheme)
                  3)}]}

      :MuiPaper
      {:variants
       [{:props {:variant "filled"}
         :style {:backgroundColor (:primaryContainer scheme)
                 :color (:onPrimaryContainer scheme)}}]
       :styleOverrides
       {:rounded {:borderRadius "16px"}
        :elevated {:backgroundImage (str "linear-gradient(" (:primary scheme) "0d , " (:primary scheme) "0d)")}
        :elevation1 {:backgroundImage (str "linear-gradient(" (:primary scheme) "0d , " (:primary scheme) "0d)")} ; alpha = 0.05
        :elevation2 {:backgroundImage (str "linear-gradient(" (:primary scheme) "14 , " (:primary scheme) "14)")} ; alpha = 0.08
        :elevation3 {:backgroundImage (str "linear-gradient(" (:primary scheme) "1C , " (:primary scheme) "1C)")} ; alpha = 0.11
        :elevation4 {:backgroundImage (str "linear-gradient(" (:primary scheme) "1D , " (:primary scheme) "1D)")} ; alpha = 0.12
        :elevation5 {:backgroundImage (str "linear-gradient(" (:primary scheme) "1F , " (:primary scheme) "1F)")}}} ; alpha = 0.14

      :MuiCard
      {:variants
       [{:props {:variant "filled"}
         :style {:backgroundColor (:surfaceVariant scheme)
                 :color (:onSurfaceVariant scheme)
                 :boxShadow "none"
                 :border "none"}}
        {:props {:variant "elevated"}
         :defaultProps {:raised true}
         :style {:boxShadow "0px 1px 2px rgba(0, 0, 0, 0.3),
                             0px 1px 3px 1px rgba(0, 0, 0, 0.15);"}}
        {:props {:variant "outlined"}
         :style {:borderColor (:outline scheme)}}]

       :styleOverrides
       {:root
        {:borderRadius 12}}}}}))


(def shared
  {:typography {:fontFamily "'Roboto Flex', 'Helvetica', 'Arial', sans-serif"}

   :palette {:gold {:main "#ffd700"}}

   :components
   {:MuiCssBaseline
    {:styleOverrides
     "@font-face {
       font-family: 'Roboto Flex';
       font-weight: 100 1000;
       font-stretch: 25% 151%;
       font-display: swap;
       src: url('/assets/fonts/RobotoFlex-VariableFont_GRAD,XTRA,YOPQ,YTAS,YTDE,YTFI,YTLC,YTUC,opsz,slnt,wdth,wght.ttf') format('truetype');
      }"}

    :MuiTooltip
    {:defaultProps
     {:arrow true}}}})


(def get-mui-theme
  #_(memoize)
  (fn
    ([theme-key] (get-mui-theme theme-key {}))
    ([theme-key {:keys [source-color]
                 :or {source-color
                      "#3a8a3a"
                      #_"#0061A7"
                      #_"#ffc0cb"}}]
     (let [theme-key (or theme-key :light)]
       (responsiveFontSizes
         (createTheme
           (deepmerge
             (clj->js shared)
             (deepmerge
               (-> source-color
                 (make-md3-theme custom-colors)
                 (md3->mui-theme theme-key)
                 clj->js)))))))))

(get-mui-theme :light)