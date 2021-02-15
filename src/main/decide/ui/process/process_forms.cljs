(ns decide.ui.process.process-forms
  (:require
    [clojure.string :as str]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [decide.models.process :as process]
    [decide.models.user :as user]
    [material-ui.data-display :as dd]
    [material-ui.inputs :as inputs]
    [material-ui.inputs.form :as form]
    [material-ui.inputs.input :as input]
    [material-ui.layout :as layout]
    [material-ui.layout.grid :as grid]
    [taoensso.timbre :as log]
    [decide.ui.common.time :as time]
    [com.fulcrologic.fulcro.dom :as dom]
    [material-ui.transitions :as transitions]
    [material-ui.data-display.list :as list]))

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
   :margin "dense"})

(defsc Participant [_ {::user/keys [display-name]}]
  {:query [::user/id ::user/display-name]
   :ident ::user/id}
  (list/item {}
    (list/item-text {} display-name)))

(def ui-participant (comp/computed-factory Participant))

(defn participant-list [{:keys [participants]}]
  (list/list {}
    (map ui-participant participants)))

(defsc NewProcessForm [_ {:keys [participants]} {:keys [onSubmit]}]
  {:query [{:participants [::user/id]}]
   :initial-state {}
   :use-hooks? true}
  (let [[title change-title] (hooks/use-state "")
        title-max-length 100
        [description change-description] (hooks/use-state "")
        [auto-slug? set-auto-slug] (hooks/use-state true)
        [slug change-slug] (hooks/use-state "")
        [with-end? set-with-end?] (hooks/use-state false)
        [end-time set-end-time] (hooks/use-state nil)
        [public? set-public?] (hooks/use-state true)
        update-title (hooks/use-callback (fn [e] (let [value (evt/target-value e)]
                                                   (change-title value)
                                                   (set-auto-slug true))))]
    (layout/box {:component :form
                 :onSubmit (fn [e]
                             (evt/prevent-default! e)
                             (onSubmit {::process/title title
                                        ::process/slug (slugify (if auto-slug? title slug))
                                        ::process/description description
                                        ::process/end-time (and with-end? end-time)}))}
      (let [length (count title)
            close-to-max? (< (- title-max-length 10) length)]
        (inputs/textfield
          (merge default-input-props
            {:label "Titel"
             :required true
             :value title
             :onChange update-title
             :error close-to-max?
             :helperText (str length "/" title-max-length)
             :autoFocus true
             :inputProps {:maxLength title-max-length}})))

      (inputs/textfield
        (merge default-input-props
          {:label "URL"
           :helperText "Erlaubt sind Kleinbuchstaben (a-z), Zahlen und Bindestriche."
           :value (if auto-slug? (slugify title) slug)
           :onChange (fn [e]
                       (let [value (evt/target-value e)]
                         (set-auto-slug false)
                         (change-slug (slugify value))))
           :inputProps {:maxLength title-max-length}
           :InputProps {:startAdornment (input/adornment {:position :start} (str (-> js/document .-location .-host) "/decision/"))}}))

      (inputs/textfield
        (merge default-input-props
          {:label "Worum geht es?"
           :multiline true
           :rows 7
           :value description
           :onChange #(change-description (evt/target-value %))}))

      (dom/div {}
        (form/group {:row true}
          (form/control-label
            {:label "Endet der Entscheidungsprozess?"
             :color "textSecondary"
             :checked with-end?
             :onChange #(set-with-end? (not with-end?))
             :control (inputs/switch {})}))

        (transitions/collapse {:in with-end?}
          (time/datetime-picker
            (merge default-input-props
              {:label "Wann?"
               :value end-time
               :required with-end?
               :disabled (not with-end?)
               :inputVariant "filled"
               :onChange set-end-time
               :fullWidth true}))))

      (dom/div {}
        (form/group {:row true}
          (form/control-label
            {:label "Soll der Prozess öffentlich sein?"
             :color "textSecondary"
             :checked public?
             :onChange #(set-public? (not public?))
             :control (inputs/switch {})}))

        (transitions/collapse {:in (not public?)}
          (layout/box {:style {:borderWidth "thin" :borderStyle "dashed"} :borderColor "warning.main"}
            (inputs/textfield
              (merge default-input-props
                {:label "Teilnehmer E-Mail Adressen"
                 :helperText "Separiere mehrere Adressen mit Kommas"}))
            (participant-list {:participants participants}))))


      (inputs/button
        {:color :primary
         :type "submit"}
        "Anlegen"))))

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