(ns decide.ui.process.new-process-form
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [com.fulcrologic.fulcro.react.hooks :as hooks]
            [decide.models.process :as process]
            [material-ui.data-display :as dd]
            [material-ui.data-display.list :as list]
            [material-ui.lab :refer [skeleton]]
            [material-ui.layout :as layout]
            [material-ui.layout.grid :as grid]
            [material-ui.inputs :as inputs]
            [com.fulcrologic.fulcro.dom.events :as evt]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [material-ui.inputs.input :as input]))

(def slug-pattern #"^[a-z0-9]+(?:-[a-z0-9]+)*$")

(defn keep-chars [s re]
  (apply str (re-seq re s)))


(defn slugify [s]
  (-> s
    str/lower-case
    str/trim
    (str/replace #"[\s-]+" "-") ; replace multiple spaces and dashes with a single dash
    (keep-chars #"[a-z0-9-]")
    (str/replace #"^-|" ""))) ; remove dash prefix


(defsc NewProcessForm [_ _ {:keys [onSubmit]}]
  {:query []
   :initial-state {}
   :use-hooks? true}
  (let [[title change-title] (hooks/use-state "")
        [description change-description] (hooks/use-state "")
        [auto-slug? set-auto-slug] (hooks/use-state true)
        [slug change-slug] (hooks/use-state "")
        update-title (hooks/use-callback (fn [e] (let [value (evt/target-value e)]
                                                   (change-title value)
                                                   (set-auto-slug true))))]
    (layout/box {:component :form
                 :onSubmit (fn [e]
                             (evt/prevent-default! e)
                             (onSubmit {::process/title title
                                        ::process/slug (slugify (if auto-slug? title slug))
                                        ::process/description description}))}
      (dd/typography {:paragraph false}
        "Gib dem Entscheidungsprozess einen Titel:")
      (inputs/textfield
        {:label "Titel"
         :variant "filled"
         :fullWidth true
         :autoComplete "off"
         :inputProps {:maxLength 140}
         :value title
         :onChange update-title
         :margin "normal"})

      (inputs/textfield
        {:label "URL"
         :fullWidth true
         :autoComplete "off"
         :helperText "Erlaubt sind Kleinbuchstaben (a-z), Zahlen und Bindestriche."
         :value (if auto-slug? (slugify title) slug)
         :onChange (fn [e]
                     (let [value (evt/target-value e)]
                       (set-auto-slug false)
                       (change-slug (slugify value))))
         :margin "normal"
         :inputProps {:maxLength 140}
         :InputProps {:startAdornment (input/adornment {:position :start} (str (-> js/document .-location .-host) "/decision/"))}})

      (dd/typography {:paragraph false}
        "Worum soll es bei dem Entscheidungsprozess gehen?")
      (inputs/textfield
        {:label        "Beschreibung"
         :variant      "filled"
         :margin       "normal"
         :fullWidth    true
         :autoComplete "off"
         :multiline    true
         :rows         7
         :value        description
         :onChange     #(change-description (evt/target-value %))})

      (inputs/button {:color :primary :type "submit"} "Anlegen"))))

(def ui-new-process-form (comp/computed-factory NewProcessForm))