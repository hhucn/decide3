(ns decide.ui.process.process-forms
  (:require
    [clojure.string :as str]
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [decide.models.process :as process]
    [decide.models.user :as user]
    [decide.utils.time :as time]
    [material-ui.data-display :as dd]
    [material-ui.inputs :as inputs]
    [material-ui.inputs.form :as form]
    [material-ui.inputs.input :as input]
    [material-ui.layout :as layout]
    [material-ui.layout.grid :as grid]
    [taoensso.timbre :as log]
    [material-ui.transitions :as transitions]
    [material-ui.data-display.list :as list]
    [material-ui.surfaces :as surfaces]))


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

(defsc NewProcessForm [_ {:keys []} {:keys [onSubmit]}]
  {:query []
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
        [participants set-participants] (hooks/use-state #{})
        [participants-field-text set-participants-field] (hooks/use-state "")
        update-title (hooks/use-callback (fn [e] (let [value (evt/target-value e)]
                                                   (change-title value)
                                                   (set-auto-slug true))))]
    (layout/box {:component :form
                 :onSubmit (fn [e]
                             (evt/prevent-default! e)
                             (onSubmit {::process/title title
                                        ::process/slug (process/slugify (if auto-slug? title slug))
                                        ::process/description description
                                        ::process/end-time (when with-end? end-time)
                                        ::process/type (if public? ::process/type.public ::process/type.private)
                                        :participant-emails (vec participants)}))}
      (let [length (count title)
            close-to-max? (< (- title-max-length 10) length)]
        (inputs/textfield
          (merge default-input-props
            {:label (i18n/trc "Title of a process" "Title")
             :required true
             :value title
             :onChange update-title
             :error close-to-max?
             :helperText (str length "/" title-max-length)
             :autoFocus true
             :inputProps {:maxLength title-max-length}})))

      (inputs/textfield
        (merge default-input-props
          {:label (i18n/trc "URL of a process" "URL")
           :helperText (i18n/tr "Allowed are: lower case letters (a-z), numbers and hyphens")
           :value (if auto-slug? (process/slugify title) slug)
           :onChange (fn [e]
                       (let [value (evt/target-value e)]
                         (set-auto-slug false)
                         (change-slug (process/slugify value))))
           :inputProps {:maxLength title-max-length}
           :InputProps {:startAdornment (input/adornment {:position :start} (str (-> js/document .-location .-host) "/decision/"))}}))

      (inputs/textfield
        (merge default-input-props
          {:label (i18n/trc "What is the process about" "What is it about?")
           :multiline true
           :rows 7
           :value description
           :onChange #(change-description (evt/target-value %))}))

      (dom/div {}
        (form/group {:row true}
          (form/control-label
            {:label (i18n/tr "Does the process end?")
             :color "textSecondary"
             :checked with-end?
             :onChange #(set-with-end? (not with-end?))
             :control (inputs/switch {})}))

        (transitions/collapse {:in with-end?}
          (time/datetime-picker
            (merge default-input-props
              {:label (i18n/trc "When does a process end?" "When?")
               :value end-time
               :required with-end?
               :disabled (not with-end?)
               :inputVariant "filled"
               :onChange set-end-time
               :fullWidth true}))))

      (dom/div {}
        (form/group {:row true}
          (form/control-label
            {:label (i18n/tr "Is the process public?")
             :color "textSecondary"
             :checked public?
             :onChange #(set-public? (not public?))
             :control (inputs/switch {})}))

        (when (seq participants)
          (surfaces/paper {:variant :outlined}
            (layout/box {:p 1}
              (input/label {:shrink true} (i18n/trc "Label for list of participants" "Participants"))
              (grid/container {:spacing 1}
                (for [p participants]
                  (grid/item {:key p}
                    (dd/chip {:label p
                              :onDelete #(set-participants (disj participants p))})))))))

        (transitions/collapse {:in (not public?)}
          (inputs/textfield
            (merge default-input-props
              {:label (i18n/trc "Label for input field" "Participant emails")
               :multiline true
               :value participants-field-text
               :onChange (fn [e]
                           (let [value (str/triml (evt/target-value e))
                                 matches (re-seq #"([\w@\.\+\u00C0-\u017F]+)[\s,;]" value)]
                             (if-not matches
                               (set-participants-field value)
                               (let [prefix-length (reduce + (map (comp count first) matches))
                                     emails (map second matches)]
                                 (set-participants (apply conj participants emails))
                                 (set-participants-field (apply str (drop prefix-length value)))))))
               :rows 1}))))


      (inputs/button
        {:color :primary
         :type "submit"}
        (i18n/trc "Submit form" "Submit")))))

(def ui-new-process-form (comp/computed-factory NewProcessForm))

(defsc Process [_ _]
  {:query [::process/slug ::process/title ::process/moderators ::process/description
           :process/features]
   :ident ::process/slug})


(defsc EditProcessForm
  "The form a moderator can use to edit a process. Duh."
  [_
   {{:keys [::process/slug ::process/title ::process/moderators ::process/description ::process/end-time :process/features]} :process}
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
          {:label (i18n/trc "Title of a process" "Title")
           :value title
           :onChange #(change-title (evt/target-value %))
           :inputProps {:maxLength 140}}))

      (inputs/textfield
        (merge default-input-props
          {:label (i18n/trc "Description of a process" "Description")
           :multiline true
           :rows 7
           :value description
           :onChange #(change-description (evt/target-value %))}))

      (form/group {:row true}
        (form/control-label
          {:label (i18n/tr "Does the process end?")
           :control
           (inputs/switch
             {:checked with-end?
              :onChange #(set-with-end? (not with-end?))})}))

      (inputs/textfield
        (merge default-input-props
          {:label (i18n/trc "End of a process" "End")
           :value end-time
           :disabled (not with-end?)
           :type "datetime-local"
           :onChange #(set-end-time (evt/target-value %))
           :InputLabelProps {:shrink true}}))

      (grid/item {:xs 12}
        (inputs/button {:color :primary :type "submit"} (i18n/trc "Submit form" "Submit"))))))

(def ui-edit-process-form (comp/computed-factory EditProcessForm))