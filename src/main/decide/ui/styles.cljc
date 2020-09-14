(ns decide.ui.styles)

(def hhu-blue "#006AB3")

(def sizing
  [:body :html {:height "100%"}])
(def body
  [:body {:background-color hhu-blue
          :overflow-x "hidden"}])
(def splashscreen
  [:#decide {:flex-direction "column"
             :height "100%"
             :display "flex"}])


(def address
  [:address
   {:display "inline"
    :font-style "inherit"}])
