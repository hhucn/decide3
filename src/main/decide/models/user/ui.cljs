(ns decide.models.user.ui
  (:require
    [com.fulcrologic.fulcro.algorithms.react-interop :as interop]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [decide.models.user :as user]
    [decide.utils.color :as color-utils]
    [material-ui.data-display :as dd]
    [material-ui.layout :as layout]
    [material-ui.styles :as styles]))

(def emoji-expr #"^(?:[\u2700-\u27bf]|(?:\ud83c[\udde6-\uddff]){2}|[\ud800-\udbff][\udc00-\udfff])[\ufe0e\ufe0f]?(?:[\u0300-\u036f\ufe20-\ufe23\u20d0-\u20f0]|\ud83c[\udffb-\udfff])?(?:\u200d(?:[^\ud800-\udfff]|(?:\ud83c[\udde6-\uddff]){2}|[\ud800-\udbff][\udc00-\udfff])[\ufe0e\ufe0f]?(?:[\u0300-\u036f\ufe20-\ufe23\u20d0-\u20f0]|\ud83c[\udffb-\udfff])?)*")

(defn first-char                                            ; this only works in cljs
  "Gets the first Grapheme Cluster from a string. A character, an emoji or nil if s is not a string"
  [s]
  (when (string? s)
    (or
      (re-find emoji-expr s)
      (str (first s)))))

(def ^:private ColoredAvatar
  (js/React.forwardRef
    (fn [props ref]
      (let [{:keys [color children] :as props} (js->clj props :keywordize-keys true)
            get-contrast-text (get-in (styles/use-theme) [:palette :getContrastText])]
        (apply dd/avatar
          (-> props
            (assoc :ref ref)
            (update :style
              assoc
              :backgroundColor color
              :color (get-contrast-text color)))
          children)))))

(def ui-colored-avatar (interop/react-factory ColoredAvatar))

(defn avatar
  "DEPRECTATED - Use `ui-avatar` or `ui-colored-avatar`"
  [{:user/keys [id display-name]}]
  (ui-colored-avatar {:color (color-utils/hash-color id)
                      :alt display-name
                      :title display-name}
    (or (first-char display-name) \?)))

(defsc Avatar [this {::user/keys [id display-name]}
               {:keys [avatar-props] :or {avatar-props {}}}]
  {:query [::user/id ::user/display-name]
   :ident ::user/id}
  (let [color (color-utils/hash-color id)
        ;; for use in AvatarGroup
        style (js->clj (comp/get-raw-react-prop this :style) :keywordize-keys true)
        className (comp/get-raw-react-prop this :className)]
    (dd/tooltip {:title display-name :arrow true}
      (ui-colored-avatar
        (-> avatar-props
          (update :style merge style)
          (update :className str " " className)
          (merge
            {:alt display-name
             :color color}))

        (or (first-char display-name) \?)))))

(def ui-avatar (comp/computed-factory Avatar {:keyfn ::user/id}))


(defn chip
  ([user] (chip user {}))
  ([{:user/keys [id display-name] :as user} chip-props]
   (layout/box {:clone true :border 0}                      ; TODO mui v5 shouldn't need this anymore
     (dd/chip
       (merge
         {:label display-name
          :avatar (avatar user)
          :variant :outlined}
         chip-props)))))