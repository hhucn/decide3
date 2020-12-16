(ns decide.ui.proposal.new-proposal
  (:require
    [clojure.string :as str]
    [clojure.tools.reader.edn :as edn]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro.algorithms.merge :as mrg]
    [com.fulcrologic.fulcro.algorithms.normalized-state :as norm]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [decide.models.argument :as argument]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.utils :as utils]
    [material-ui.data-display :as dd]
    [material-ui.feedback :as feedback]
    [material-ui.inputs :as inputs]
    [material-ui.layout :as layout]
    [material-ui.navigation :as navigation]
    [material-ui.navigation.stepper :as stepper]
    ["@material-ui/icons/AddBoxOutlined" :default AddBox]
    ["@material-ui/icons/MergeType" :default MergeType]
    ["@material-ui/icons/RemoveCircleOutline" :default RemoveIcon]))

(defsc Argument [this {::argument/keys [content]
                       :keys           [:ui/new-proposal-checked?]
                       :or             {new-proposal-checked? false}}]
  {:query [::argument/id ::argument/content :ui/new-proposal-checked?]
   :ident ::argument/id}
  (dd/list-item {}
    (dd/list-item-icon {}
      (inputs/checkbox
        {:checked new-proposal-checked?
         :onClick #(m/toggle! this :ui/new-proposal-checked?)}))
    (dd/list-item-text {} content)))

(def ui-argument (comp/computed-factory Argument {:keyfn ::argument/id}))

(defsc ParentArgumentSection [this {::proposal/keys [title arguments]}]
  {:query [::proposal/id ::proposal/title
           {::proposal/arguments (comp/get-query Argument)}]
   :ident ::proposal/id}
  (when-not (empty? arguments)
    (dd/list
      {:dense     true
       :subheader (dd/list-subheader {} title)}
      (map ui-argument arguments))))

(def ui-parent-argument-section (comp/computed-factory ParentArgumentSection {:keyfn ::proposal/id}))


(defsc ParentListItem [_this {::proposal/keys [nice-id title]} {:keys [onDelete]}]
  {:query [::proposal/id
           ::proposal/nice-id
           ::proposal/title
           {::proposal/arguments (comp/get-query Argument)}]
   :ident ::proposal/id}
  (dd/list-item {}
    (dd/list-item-icon {} (dom/span (str "#" nice-id)))
    (dd/list-item-text {} (str title))
    (dd/list-item-secondary-action {}
      (inputs/icon-button
        {:onClick onDelete
         :title "Elternteil entfernen"}
        (comp/create-element RemoveIcon #js {:color "error"} nil)))))

(def ui-parent-list-item (comp/computed-factory ParentListItem {:keyfn ::proposal/id}))

(defn load-parents-with-arguments! [app-or-comp & parent-idents]
  (run!
    #(df/load! app-or-comp % ParentArgumentSection
       {:parallel true
        :focus    [::proposal/arguments]})
    parent-idents))

;;; region Mutations
(defmutation add-parent [{:keys [parent/ident]}]
  (action [{:keys [app state ref]}]
    (swap! state targeting/integrate-ident* ident :append (conj ref :ui/parents))
    (load-parents-with-arguments! app ident)))

(defn- remove-checked-from-all-arguments [state]
  (update state ::argument/id #(into {} (map (fn [[k v]] [k (dissoc v :ui/new-proposal-checked?)])) %)))

(defmutation remove-parent [{:keys [parent/ident]}]
  (action [{:keys [state ref]}]
    (norm/swap!-> state
      (mrg/remove-ident* ident (conj ref :ui/parents))
      remove-checked-from-all-arguments)))

(defn- id-in-parents? [parents id]
  ((set (map ::proposal/id parents)) id))

(defmutation show [{:keys [id title body parents]
                    :or   {title "" body "" parents []} :as params}]
  (action [{:keys [app state]}]
    (swap! state update-in [::process/slug id]
      assoc
      :ui/open? true
      :ui/title title
      :ui/body body
      :ui/parents parents
      :ui/step (if (empty? parents) 0 1))
    (apply load-parents-with-arguments! app parents)))

(defmutation reset-form [_]
  (action [{:keys [state ref]}]
    (norm/swap!-> state
      (update-in ref
        assoc
        :ui/title ""
        :ui/body ""
        :ui/parents []
        :ui/step 0)
      remove-checked-from-all-arguments)))
;;; endregion

(defn get-argument-idents-from-proposal [{::proposal/keys [arguments]}]
  (->> arguments
    (filter :ui/new-proposal-checked?)
    (map (partial comp/get-ident Argument))))

(defn- parent-selection [*this {:ui/keys       [parents]
                                ::process/keys [proposals]}]
  (layout/box {:my 1}
    (dd/list {}
      (for [{::proposal/keys [id] :as props} parents
            :let [delete-parent #(comp/transact! *this [(remove-parent {:parent/ident [::proposal/id id]})])]]
        (ui-parent-list-item props
          {:onDelete delete-parent}))
      (inputs/textfield {:label     "Eltern hinzufügen"
                         :margin    "normal"
                         :select    true
                         :value     ""
                         :fullWidth true
                         :onChange  #(comp/transact! *this [(add-parent {:parent/ident (edn/read-string (evt/target-value %))})])}
        (for [{::proposal/keys [id title]} proposals
              :when (not (id-in-parents? parents id))
              :let [proposal-ident [::proposal/id id]]]
          (navigation/menu-item {:key id :value (str proposal-ident)}
            (str "#" id " " title)))))))

(defn- argument-selection [*this {:ui/keys [parents]}]
  (layout/box {:border 0 :borderColor "grey.700"}
    (dd/typography {:component "h3"} "Welche Argumente sind immer noch relevant?")
    (let [parent-argument-sections (remove nil? (map ui-parent-argument-section parents))]
      (if-not (empty? parent-argument-sections)
        parent-argument-sections
        (dd/typography {:variant "subtitle1" :color "textSecondary"} "Die Eltern dieses Vorschlags habe keine Argumente.")))))

(defn button-card [opts & children]
  (inputs/button
    (merge
      {:style     {:height "100%"}
       :size      "large"
       :variant   "outlined"
       :fullWidth true}
      opts)
    (apply layout/box {:display "flex" :flexDirection "column"} children)))

(defn- section [& children]
  (apply dd/typography {:variant "overline" :component "h3"} children))

(defn- quoted-body [& children]
  (apply dd/typography
    {:variant   "body1"
     :paragraph true
     :style     {:borderLeft  "solid 1px black"
                 :paddingLeft "1em"
                 :whiteSpace  "pre-line"}}
    children))

(defsc NewProposalFormDialog [this {:ui/keys       [open? title body parents step]
                                    ::process/keys [slug proposals] :as props}]
  {:query         [::process/slug
                   :ui/open?
                   :ui/title :ui/body
                   {:ui/parents (comp/get-query ParentListItem)}
                   {::process/proposals (comp/get-query ParentListItem)}
                   :ui/step]
   :ident         ::process/slug
   :initial-state (fn [{:keys [id]}]
                    (merge
                      #:ui{:open?   false
                           :title   ""
                           :body    ""
                           :parents []
                           :step    0}
                      {::process/slug id}))
   :use-hooks?    true}
  (let [close-dialog (hooks/use-callback #(m/set-value! this :ui/open? false))
        reset-form (hooks/use-callback #(comp/transact! this [(reset-form {})] {:compressible? true}))
        set-step (fn set-step [step-no] (m/set-integer!! this :ui/step :value step-no))
        next-step (hooks/use-callback (fn next-step [] (set-step (inc step))) [step])]
    (feedback/dialog
      {:open       open?
       :fullWidth  true
       :fullScreen (utils/<=-breakpoint? "xs")
       :maxWidth   "md"
       :onClose    close-dialog
       :onExit     reset-form
       :PaperProps {:component "form"
                    :onSubmit  (fn submit-new-proposal-form [e]
                                 (evt/prevent-default! e)
                                 (comp/transact! this
                                   [(process/add-proposal
                                      {::process/slug       slug
                                       ::proposal/id        (tempid/tempid)
                                       ::proposal/title     title
                                       ::proposal/body      body
                                       ::proposal/parents   (mapv #(select-keys % [::proposal/id]) parents)
                                       ::proposal/arguments (vec (mapcat get-argument-idents-from-proposal parents))})])
                                 (close-dialog))}}
      (feedback/dialog-title {} "Neuer Vorschlag")
      (feedback/dialog-content {}
        (stepper/stepper
          {:activeStep  step
           :orientation "vertical"
           :nonLinear   true}

          ;; region Typ Step
          (stepper/step
            {:completed (pos? step)}
            (stepper/step-button {:onClick #(set-step 0)} "Typ")
            (stepper/step-content {}
              (dd/typography {:paragraph true}
                "Möchtest du einen neuen Vorschlag hinzufügen, einen bestehenden erweitern oder Vorschläge zusammenführen?")
              (layout/grid {:container true :spacing 2}
                (layout/grid {:item true :sm 6 :xs 12}
                  (button-card
                    {:onClick   #(set-step 2)
                     :startIcon (comp/create-element AddBox nil nil)}
                    "Neu"
                    (dd/typography {:variant "caption" :color "textSecondary"}
                      "Einen neuen Vorschlag erstellen, der nicht mit einem anderen verwandt ist.")))

                (layout/grid {:item true :sm 6 :xs 12}
                  (button-card
                    {:onClick   #(set-step 1)
                     :startIcon (layout/box {:clone true :css {:transform "rotate(.5turn)"}}
                                  (comp/create-element MergeType nil nil))}
                    "Ableiten / Zusammenführen"
                    (dd/typography {:variant "caption" :color "textSecondary"}
                      "Ein Vorschlag erstellen mit einem oder mehreren Vorgängern."))))))
          ;; endregion

          ;; region Parents Step
          (stepper/step
            {:completed (< 1 step)}
            (stepper/step-button
              {:onClick  #(set-step 1)
               :style    {:textAlign "start"}
               :optional (dd/typography {:variant "caption" :color "textSecondary"} "Optional")}
              "Eltern")
            (stepper/step-content {}
              (dd/typography {:paragraph false}
                "Von welchen bestehenden Vorschlägen hängt dein neuer Vorschlag ab?")
              (dd/typography {:paragraph false :variant "caption"}
                "Du kannst einen oder mehrere wählen.")
              (parent-selection this props)
              (inputs/button
                {:color   "primary"
                 :onClick next-step}
                "Weiter")))
          ;; endregion

          ;; region Details Step
          (let [change-title (hooks/use-callback (partial m/set-string! this :ui/title :event))
                change-body (hooks/use-callback (partial m/set-string! this :ui/body :event))
                error? (or
                         (str/blank? title)
                         (str/blank? body))]
            (stepper/step
              {:completed
               (and (< 2 step) (not error?))}
              (stepper/step-button
                {:onClick  #(set-step 2)
                 :style    {:textAlign "start"}             ; fix for text alignment in button
                 :optional (when (and (< 2 step) error?)
                             (dd/typography {:variant "caption" :color "error"}
                               "Titel oder Details dürfen nicht leer sein."))}
                (stepper/step-label
                  {:error (and (< 2 step) error?)}
                  "Details"))

              (stepper/step-content {}
                (dd/typography {:paragraph false}
                  "Gib dem Vorschlag einen erkennbaren Titel:")
                (inputs/textfield
                  {:label        "Titel"
                   :variant      "outlined"
                   :fullWidth    true
                   :autoComplete "off"
                   :value        title
                   :onChange     change-title
                   :margin       "normal"})

                ;; Body
                (dd/typography {:paragraph false}
                  "Füge Details hinzu, damit andere wissen, worum es sich bei deinem Vorschlag handelt:")
                (inputs/textfield
                  {:label        "Details"
                   :variant      "outlined"
                   :margin       "normal"
                   :fullWidth    true
                   :autoComplete "off"
                   :multiline    true
                   :rows         7
                   :value        body
                   :onChange     change-body})

                (inputs/button
                  {:color    "primary"
                   :disabled error?
                   :onClick  #(set-step (if (empty? parents)
                                          4 3))}
                  "Weiter"))))
          ;; endregion

          ;; region Parents Step
          (stepper/step
            {:disabled  (empty? parents)
             :completed (< 3 step)}
            (stepper/step-button
              {:onClick  #(set-step 3)
               :style    {:textAlign "start"}
               :optional (dd/typography {:variant "caption" :color "textSecondary"}
                           (if (empty? parents)
                             "Nur mit Eltern möglich."
                             "Optional"))}
              "Argumente")
            (stepper/step-content {}
              (argument-selection this props)
              (inputs/button
                {:color   "primary"
                 :onClick next-step}
                "Weiter")))
          ;; endregion

          (stepper/step {}
            (stepper/step-button
              {:onClick #(set-step 4)}
              "Übersicht")
            (stepper/step-content {}
              (layout/box {:mb 2}
                (section "Titel:")
                (quoted-body title)

                (section "Details:")
                (quoted-body body)

                (when-not (empty? parents)
                  (comp/fragment
                    (section
                      "Abhängig von "
                      (if (< 1 (count parents))
                        (str (count parents) " anderen Vorschlägen")
                        "einem anderem Vorschlag")
                      ":")
                    (dd/list {:dense true :disablePadding true}
                      (for [{::proposal/keys [id title]} parents]
                        (dd/list-item {:key id}
                          (dd/list-item-icon {} (dom/span (str "#" id)))
                          (dd/list-item-text {} (str title))))))))

              (inputs/button {:color   "primary"
                              :type    "submit"
                              :variant "contained"}
                "Abschicken")))))


      (feedback/dialog-actions {}
        (inputs/button {:onClick close-dialog} "Abbrechen")))))

(def ui-new-proposal-form (comp/computed-factory NewProposalFormDialog))