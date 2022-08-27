(ns decide.ui.theming.themes
  (:require
   ["@mui/material/styles" :refer [responsiveFontSizes createTheme]]
   ["@mui/utils" :refer [deepmerge]]
   [decide.ui.theming.styles :as styles]))

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
     {:arrow true}}
    :MuiCard
    {:styleOverrides
     {:root
      {:borderRadius 12
       :border "1px solid rgba(0, 0, 0, 0.12)"}}}}})

(def light-theme
  {:palette
   {:mode "light"
    :background {:default "#fbfbfc"}
    :primary {:main styles/hhu-blue}
    :secondary {:main "#b3006b"}
    :success {:main "#008127"}}})

(def dark-theme
  {:palette
   {:mode "dark"
    :background {:default "#121416", :paper "#101519"}
    :primary {:main "#68b9e9"}
    :secondary {:main "#f996c7"}
    :success {:main "#7dc75f"}
    :error {:main "#e8415d"}
    :gold {:main "#ffd700"}}
   :components
   {:MuiButton
    {:variations
     {:props {:variation "label"}
      :defaultProps {:size "small"
                     :color "inherit"}}}}})

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