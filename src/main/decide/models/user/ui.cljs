(ns decide.models.user.ui
  (:require
    [com.fulcrologic.fulcro.algorithms.react-interop :as interop]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [decide.models.user :as user]
    [decide.utils.color :as color-utils]
    [mui.data-display :as dd]
    [mui.data-display.list :as list]
    [mui.styles :as styles]
    [clojure.set :as set]))

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

(defn- wrap-with-user-tooltip [user & body]
  (apply
    dd/tooltip
    {:arrow true
     :title (list/list {:dense true}
              (list/item {:disableGutters true} (::user/display-name user)))}
    body))

(defsc Avatar [this {::user/keys [id display-name] :as user}
               {:keys [avatar-props tooltip?]
                :or {avatar-props {}
                     tooltip? false}}]
  {:query [::user/id ::user/display-name]
   :ident ::user/id}
  (let [color (color-utils/hash-color id)
        ;; for use in AvatarGroup
        style (js->clj (comp/get-raw-react-prop this :style) :keywordize-keys true)
        className (comp/get-raw-react-prop this :className)]
    (cond->>
      (ui-colored-avatar
        (-> avatar-props
          (update :style merge style)
          (update :className str " " className)
          (merge
            {:alt display-name
             :color color}))

        (or (first-char display-name) \?))
      tooltip? (wrap-with-user-tooltip user))))

(def ui-avatar (comp/computed-factory Avatar {:keyfn ::user/id}))


(defn chip
  ([user] (chip user {}))
  ([{:user/keys [id display-name] :as user} chip-props]
   (dd/chip
     (merge
       {:label display-name
        :avatar (ui-avatar #::user{:id id, :display-name display-name})
        :sx {:border 0}
        :variant :outlined}
       chip-props))))

(defn avatar-group [{:keys [max] :as props} children]
  (dd/avatar-group (update props :max inc)
    (doall
      (let [[to-display overflow] (split-at (dec max) children)]
        (concat
          (map #(ui-avatar % {:tooltip? true}) to-display)
          [(cond
             (= 1 (count overflow)) (ui-avatar (first overflow) {:tooltip? true})

             ;; Display "+42" overflow Avatar
             (< 1 (count overflow))
             (dd/tooltip {:title (list/list {:dense true}
                                   (doall
                                     (for [{::user/keys [display-name]} overflow]
                                       (list/item {:disableGutters true} display-name))))
                          :arrow true}
               (dd/avatar {} (str "+" (count overflow)))))])))))