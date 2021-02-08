(ns decide.ui.process.process-forms
  (:require
    [clojure.string :as str]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [decide.models.process :as process]
    [material-ui.data-display :as dd]
    [material-ui.inputs :as inputs]
    [material-ui.inputs.form :as form]
    [material-ui.inputs.input :as input]
    [material-ui.layout :as layout]
    [material-ui.layout.grid :as grid]
    [taoensso.timbre :as log]))

(def slug-pattern #"^[a-z0-9]+(?:-[a-z0-9]+)*$")

(defn keep-chars [s re]
  (apply str (re-seq re s)))


(defn slugify [s]
  (-> s
    str/lower-case
    str/trim
    (str/replace #"[\s-]+" "-")                             ; replace multiple spaces and dashes with a single dash
    (keep-chars #"[a-z0-9-]")
    (str/replace #"^-|" "")))                               ; remove dash prefix

(def default-input-props
  {:fullWidth true
   :variant "filled"
   :autoComplete "off"
   :margin "normal"})

(defsc NewProcessForm [_ _ {:keys [onSubmit]}]
  {:query []
   :initial-state {}
   :use-hooks? true}
  (let [[title change-title] (hooks/use-state "")
        [description change-description] (hooks/use-state "")
        [auto-slug? set-auto-slug] (hooks/use-state true)
        [slug change-slug] (hooks/use-state "")
        [with-end? set-with-end?] (hooks/use-state false)
        [end-time set-end-time] (hooks/use-state "")
        update-title (hooks/use-callback (fn [e] (let [value (evt/target-value e)]
                                                   (change-title value)
                                                   (set-auto-slug true))))]
    (layout/box {:component :form
                 :onSubmit (fn [e]
                             (evt/prevent-default! e)
                             (onSubmit {::process/title title
                                        ::process/slug (slugify (if auto-slug? title slug))
                                        ::process/description description
                                        ::process/end-time (and end-time (js/Date. end-time))}))}
      (dd/typography {:paragraph false}
        "Gib dem Entscheidungsprozess einen Titel:")

      (inputs/textfield
        (merge default-input-props
          {:label "Titel"
           :value title
           :onChange update-title
           :inputProps {:maxLength 140}}))

      (inputs/textfield
        (merge default-input-props
          {:label "URL"
           :helperText "Erlaubt sind Kleinbuchstaben (a-z), Zahlen und Bindestriche."
           :value (if auto-slug? (slugify title) slug)
           :onChange (fn [e]
                       (let [value (evt/target-value e)]
                         (set-auto-slug false)
                         (change-slug (slugify value))))
           :inputProps {:maxLength 140}
           :InputProps {:startAdornment (input/adornment {:position :start} (str (-> js/document .-location .-host) "/decision/"))}}))

      (dd/typography {:paragraph false}
        "Worum soll es bei dem Entscheidungsprozess gehen?")
      (inputs/textfield
        (merge default-input-props
          {:label "Beschreibung"
           :multiline true
           :rows 7
           :value description
           :onChange #(change-description (evt/target-value %))}))

      (form/group {:row true}
        (form/control-label
          {:label "Hat der Prozess ein Ende?"
           :control
           (inputs/switch
             {:checked with-end?
              :onChange #(set-with-end? (not with-end?))})}))

      (inputs/textfield
        (merge default-input-props
          {:label "Ende"
           :type "datetime-local"
           :disabled (not with-end?)
           :value end-time
           :onChange #(set-end-time (evt/target-value %))
           :InputLabelProps {:shrink true}}))


      (inputs/button {:color :primary :type "submit"} "Anlegen"))))

(def ui-new-process-form (comp/computed-factory NewProcessForm))

(defsc Process [_ _]
  {:query [::process/slug ::process/title ::process/moderators ::process/description]
   :ident ::process/slug})


(defsc EditProcessForm
  "The form a moderator can use to edit a process. Duh."
  [_
   {{::process/keys [slug title moderators description end-time]} :process}
   {:keys [onSubmit]}]
  {:query [{:process (comp/get-query Process)}]
   :initial-state
   (fn [{:keys [process] :as params}]
     (if (::process/slug process)
       {:process process}
       (log/error
         "Initial state for" `EditProcessForm "needs to have a" :process "key with the key" ::process/slug "."
         "Provided: " params)))
   :use-hooks? true}
  (let [[title change-title] (hooks/use-state title)
        [description change-description] (hooks/use-state description)
        [with-end? set-with-end?] (hooks/use-state (boolean end-time))
        [end-time set-end-time] (hooks/use-state (or end-time ""))]
    (grid/container
      {:component :form
       :onSubmit (fn [e]
                   (evt/prevent-default! e)
                   (onSubmit
                     {::process/title title
                      ::process/slug slug
                      ::process/description description
                      ::process/end-time (and end-time (js/Date. end-time))}))}
      (inputs/textfield
        (merge default-input-props
          {:label "Titel"
           :value title
           :onChange #(change-title (evt/target-value %))
           :inputProps {:maxLength 140}}))

      (inputs/textfield
        (merge default-input-props
          {:label "Beschreibung"
           :multiline true
           :rows 7
           :value description
           :onChange #(change-description (evt/target-value %))}))

      (form/group {:row true}
        (form/control-label
          {:label "Hat der Prozess ein Ende?"
           :control
           (inputs/switch
             {:checked with-end?
              :onChange #(set-with-end? (not with-end?))})}))

      (inputs/textfield
        (merge default-input-props
          {:label "Ende"
           :value end-time
           :disabled (not with-end?)
           :type "datetime-local"
           :onChange #(set-end-time (evt/target-value %))
           :InputLabelProps {:shrink true}}))

      (grid/item {:xs 12}
        (inputs/button {:color :primary :type "submit"} "Speichern")))))

(def ui-edit-process-form (comp/computed-factory EditProcessForm))