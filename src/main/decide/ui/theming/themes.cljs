(ns decide.ui.theming.themes
  (:require
   ["@mui/material/styles" :refer [responsiveFontSizes createTheme]]
   [decide.ui.theming.md3 :as md3]
   ["@material/material-color-utilities" :refer [argbFromHex hexFromArgb]]))


(def custom-colors
  [(md3/make-custom-color "success" "#00cc3d" true)
   (md3/make-custom-color "warning" "#ffc266" true)
   (md3/make-custom-color "info" "#0288d1" true)
   (md3/make-custom-color "gold" "#ffd700" false)])


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


(defn design-tokens [theme mode]
  (let [palette   (md3/mui-palette theme mode)
        color-map (fn color [color-key]
                    {:background (get-in palette [color-key :main])
                     :color (get-in palette [color-key :contrastText])})
        primary   (get-in palette [:primary :main])]
    {:palette palette

     :typography
     {:fontFamily "'Roboto Flex', 'Helvetica', 'Arial', sans-serif"

      :button
      {:lineHeight "16px"
       :textTransform :none}}

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
       {:arrow true}}

      :MuiFab
      {:variants
       [{:props {:color :tertiary}
         :style
         (merge
           (color-map :tertiaryContainer)
           {"&:hover" {:background (get-in palette [:tertiaryContainer :main])}
            "&:after" {:background (get-in palette [:tertiaryContainer :contrastText])}})}]
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
        {:minHeight "56px"}}}

      :MuiDialog
      {:styleOverrides
       {:paper
        (with-elevation
          (merge
            (color-map :surface)
            {:borderRadius "24px"
             :paddingTop "8px"
             :paddingBottom "8px"})
          primary
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
        {:minHeight "40px"
         :borderWidth "1px"
         :borderRadius "50rem"
         :paddingRight "24px"
         :paddingLeft "24px"}
        :startIcon {:marginLeft "-8px"}
        :endIcon {:marginRight "-8px"}}}

      :MuiAlert
      {:styleOverrides
       {:standardWarning
        (color-map :warningContainer)}}

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
         :style (color-map :surface)}
        {:props {:variant "on-scroll"}
         :style (with-elevation
                  (color-map :surface)
                  primary
                  3)}]}

      :MuiPaper
      {:variants
       [{:props {:variant "filled"}
         :style (color-map :primaryContainer)}]
       :styleOverrides
       {:rounded {:borderRadius "16px"}
        :elevated {:backgroundImage (str "linear-gradient(" primary "0d , " primary "0d)")}
        :elevation1 {:backgroundImage (str "linear-gradient(" primary "0d , " primary "0d)")} ; alpha = 0.05
        :elevation2 {:backgroundImage (str "linear-gradient(" primary "14 , " primary "14)")} ; alpha = 0.08
        :elevation3 {:backgroundImage (str "linear-gradient(" primary "1C , " primary "1C)")} ; alpha = 0.11
        :elevation4 {:backgroundImage (str "linear-gradient(" primary "1D , " primary "1D)")} ; alpha = 0.12
        :elevation5 {:backgroundImage (str "linear-gradient(" primary "1F , " primary "1F)")}}} ; alpha = 0.14

      :MuiCard
      {:variants
       [{:props {:variant "filled"}
         :style
         (merge
           (color-map :surfaceVariant)
           {:boxShadow "none"
            :border "none"})}
        {:props {:variant "elevated"}
         :defaultProps {:raised true}
         :style {:boxShadow "0px 1px 2px rgba(0, 0, 0, 0.3),
                             0px 1px 3px 1px rgba(0, 0, 0, 0.15);"}}
        {:props {:variant "outlined"}
         :style {:borderColor (get-in palette [:outline :main])}}]

       :styleOverrides
       {:root
        {:borderRadius 12}}}}}))


(def get-mui-theme
  (memoize
    (fn
      ([] (get-mui-theme {:mode :light}))
      ([{:keys [source-color mode]
         :or {mode :light
              source-color
              "#0061A7"
              #_"#009e2d"
              #_"#ffc0cb"}}]
       (responsiveFontSizes
         (createTheme
           (-> source-color
             (md3/make-theme custom-colors)
             (design-tokens mode)
             clj->js)))))))