(ns decide.models.user.ui
  (:require
    [decide.models.user :as user]
    [decide.utils.color :as color-utils]
    [material-ui.data-display :as dd]
    [material-ui.styles :as styles]
    [material-ui.layout :as layout]))

(def emoji-expr #"^(?:[\u2700-\u27bf]|(?:\ud83c[\udde6-\uddff]){2}|[\ud800-\udbff][\udc00-\udfff])[\ufe0e\ufe0f]?(?:[\u0300-\u036f\ufe20-\ufe23\u20d0-\u20f0]|\ud83c[\udffb-\udfff])?(?:\u200d(?:[^\ud800-\udfff]|(?:\ud83c[\udde6-\uddff]){2}|[\ud800-\udbff][\udc00-\udfff])[\ufe0e\ufe0f]?(?:[\u0300-\u036f\ufe20-\ufe23\u20d0-\u20f0]|\ud83c[\udffb-\udfff])?)*")

(defn first-char ; this only works in cljs
  "Gets the first Grapheme Cluster from a string. A character, an emoji or nil if s is not a string"
  [s]
  (when (string? s)
    (or
      (re-find emoji-expr s)
      (str (first s)))))

(defn avatar
  ([user] (avatar user {}))
  ([{::user/keys [id display-name]} avatar-props]
   (let [color (color-utils/hash-color id)
         get-contrast-text (get-in (styles/use-theme) [:palette :getContrastText])]
     (dd/avatar
       (-> avatar-props
         (assoc :alt display-name)
         (update :style assoc
           :backgroundColor color
           :color (get-contrast-text color)))
       (or (first-char display-name) \?)))))

(defn chip
  ([user] (chip user {}))
  ([{::user/keys [display-name] :as user} chip-props]
   (layout/box {:clone true :border 0}                      ; TODO mui v5 shouldn't need this anymore
     (dd/chip
       (merge
         {:label display-name
          :avatar (avatar user)
          :variant :outlined}
         chip-props)))))