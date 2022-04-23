(ns decide.ui.process.process-forms
  (:require
   [clojure.string :as str]
   [com.fulcrologic.fulcro-i18n.i18n :as i18n]
   [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
   [com.fulcrologic.fulcro.dom :as dom]
   [com.fulcrologic.fulcro.dom.events :as evt]
   [com.fulcrologic.fulcro.mutations :as m]
   [com.fulcrologic.fulcro.react.hooks :as hooks]
   [decide.models.process :as model.process]
   [decide.models.user :as user]
   [decide.process :as process]
   [decide.utils.slugify :as slugify]
   [mui.data-display :as dd]
   [mui.data-display.list :as list]
   [mui.inputs :as inputs]
   [mui.inputs.form :as form]
   [mui.inputs.input :as input]
   [mui.layout :as layout]
   [mui.layout.grid :as grid]
   [mui.surfaces :as surfaces]
   [mui.transitions :as transitions]
   [mui.x.date-pickers :as date-pickers]
   [taoensso.timbre :as log]))


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

(def ui-participant (comp/computed-factory Participant {:keyfn ::user/id}))

(defn participant-list [{:keys [participants]}]
  (list/list {}
    (map ui-participant participants)))

(defsc NewProcessForm [this {:ui/keys [title slug description with-end? public?]} {:keys [onSubmit]}]
  {:ident (fn [] [:form/id ::NewProcessForm])
   :query [:ui/title
           :ui/slug
           :ui/description
           :ui/with-end?
           :ui/public?]
   :initial-state {:ui/title ""
                   :ui/slug ""
                   :ui/description ""
                   :ui/with-end? false
                   :ui/public? true}
   :use-hooks? true}
  (let [title-max-length process/title-char-limit
        [end-time set-end-time] (hooks/use-state nil)
        [participants set-participants] (hooks/use-state #{})
        [participants-field-text set-participants-field] (hooks/use-state "")
        update-title     (hooks/use-callback
                           (fn [e] (let [value (evt/target-value e)]
                                     (comp/transact!! this
                                       [(m/set-props {:ui/title value
                                                      :ui/slug (if (str/blank? value)
                                                                 value
                                                                 (slugify/slugify value))})]
                                       {:compressible? true}))))]
    (layout/box {:component :form
                 :onSubmit (fn [e]
                             (evt/prevent-default! e)
                             (onSubmit {::model.process/title title
                                        ::model.process/slug slug
                                        ::model.process/description description
                                        ::model.process/end-time (when with-end? end-time)
                                        ::model.process/type (if public? ::model.process/type.public ::model.process/type.private)
                                        :participant-emails (vec participants)}))}
      (let [length (count title)
            close-to-max? (< (- title-max-length 10) length)]
        (inputs/textfield
          (merge default-input-props
            {:label (i18n/trc "Title of a process" "Title")
             :required true
             :value title
             :onChange update-title
             :helperText (when close-to-max?
                           (str length "/" title-max-length))
             :autoFocus true
             :inputProps {:maxLength title-max-length}})))

      (inputs/textfield
        (merge default-input-props
          {:label (i18n/trc "URL of a process" "URL")
           :helperText (i18n/tr "Allowed are: lower case letters (a-z), numbers and hyphens")
           :value slug
           :onChange
           (fn [e]
             (let [value (evt/target-value e)]
               (m/set-string!! this :ui/slug :value
                 (if (str/blank? value)
                   value
                   (slugify/slugify value)))))
           :inputProps {:maxLength title-max-length}
           :InputProps {:startAdornment (input/adornment {:position :start} (str (-> js/document .-location .-host) "/decision/"))}}))

      (inputs/textfield
        (merge default-input-props
          {:label (i18n/trc "What is the process about" "What is it about?")
           :multiline true
           :rows 7
           :value description
           :onChange #(m/set-string!! this :ui/description :event %)}))

      (dom/div {}
        (form/group {:row true}
          (form/control-label
            {:label (i18n/tr "Does the process end?")
             :color "textSecondary"
             :checked with-end?
             :onChange #(m/toggle!! this :ui/with-end?)
             :control (inputs/switch {})}))

        (transitions/collapse {:in with-end?}
          (date-pickers/date-time-picker
            {:renderInput #(inputs/textfield (merge (js->clj %) default-input-props {}))
             :value end-time
             :disabled (not with-end?)
             :onChange set-end-time
             :label (i18n/trc "When does a process end?" "When?")})))

      (dom/div {}
        (form/group {:row true}
          (form/control-label
            {:label (i18n/tr "Is the process public?")
             :color "textSecondary"
             :checked public?
             :onChange #(m/toggle!! this :ui/public?)
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
  {:query [::model.process/slug ::model.process/title ::model.process/moderators ::model.process/description
           :process/features]
   :ident ::model.process/slug})


(defsc EditProcessForm
  "The form a moderator can use to edit a process. Duh."
  [_
   {{:keys [::model.process/slug ::model.process/title ::model.process/moderators ::model.process/description ::model.process/end-time :process/features]} :process}
   {:keys [onSubmit]}]
  {:query [{:process (comp/get-query Process)}]
   :initial-state
   (fn [{:keys [process] :as params}]
     (if (::model.process/slug process)
       {:process process}
       (log/error
         "Initial state for" `EditProcessForm "needs to have a" :process "key with the key" ::model.process/slug "."
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
                     {::model.process/title title
                      ::model.process/slug slug
                      ::model.process/description description
                      ::model.process/end-time (and end-time (js/Date. end-time))}))}
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