(ns decide.ui.proposal.new-proposal
  (:require
    [cljs.spec.alpha :as s]
    [clojure.string :as str]
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
    [com.fulcrologic.guardrails.core :refer [>defn >defn- =>]]
    [decide.models.argument :as argument]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.utils.breakpoint :as breakpoint]
    [material-ui.data-display :as dd]
    [material-ui.data-display.list :as list]
    [material-ui.feedback.dialog :as dialog]
    [material-ui.inputs :as inputs]
    [material-ui.layout :as layout]
    [material-ui.layout.grid :as grid]
    [material-ui.navigation.stepper :as stepper]
    ["@material-ui/icons/AddBoxOutlined" :default AddBox]
    ["@material-ui/icons/MergeType" :default MergeType]
    ["@material-ui/icons/FileCopyOutlined" :default FileCopy]))


(declare NewProposalFormDialog)

(defmutation reset-form [_]
  (action [{:keys [state ref]}]
    (swap! state
      mrg/merge-component NewProposalFormDialog
      (comp/get-initial-state NewProposalFormDialog {:slug (second ref)}))))

;; region Argument Step
(defmutation toggle-argument [{::argument/keys [ident]}]
  (action [{:keys [state ref]}]
    (swap! state
      update-in ref
      update :arguments
      (fn [arguments]
        (let [arguments (set arguments)]
          (if (arguments ident)
            (disj arguments ident)
            (conj arguments ident)))))))

(defsc Argument [this {::argument/keys [content type]}
                 {:keys [checked? toggle-argument]}]
  {:query [::argument/id ::argument/content ::argument/type]
   :ident ::argument/id}
  (list/item {}
    (list/item-icon {}
      (inputs/checkbox
        {:edge :start
         :checked checked?
         :onClick #(toggle-argument (comp/get-ident this))}))
    (list/item-text {:primary content}
      (case type
        :pro (layout/box {:color "success.main"} "Pro:")
        :contra (layout/box {:color "error.main"} "Con:"))
      content)))

(def ui-argument (comp/computed-factory Argument {:keyfn ::argument/id}))

(defsc ParentArgumentSection [_ {::proposal/keys [title arguments]}
                              {:keys [selected-arguments toggle-argument]
                               :or {selected-arguments {}}}]
  {:query [::proposal/id ::proposal/title
           {::proposal/arguments (comp/get-query Argument)}]
   :ident ::proposal/id}
  (list/list
    {:dense true
     :subheader (list/subheader {} title)}
    (for [argument arguments
          :let [checked? (contains? (set selected-arguments) (comp/get-ident Argument argument))]]
      (ui-argument argument {:checked? checked?
                             :toggle-argument toggle-argument}))))

(def ui-parent-argument-section (comp/computed-factory ParentArgumentSection {:keyfn ::proposal/id}))

(defn load-parents-with-arguments! [app-or-comp & parent-idents]
  (run!
    #(df/load! app-or-comp % ParentArgumentSection
       {:parallel true
        :focus [::proposal/arguments]})
    parent-idents))


(defsc ArgumentStep [this {} {:keys [parents toggle-argument selected-arguments]}]
  {:use-hooks? true}
  (hooks/use-lifecycle
    #(apply load-parents-with-arguments! this (map (fn [parent] (find parent ::proposal/id)) parents)))
  (comp/fragment
    (dd/typography {:component "h3"} "Welche Argumente sind immer noch relevant?")
    (let [parents-with-arguments (remove #(->> % ::proposal/arguments empty?) parents)]
      (if (empty? parents-with-arguments)
        (dd/typography {:variant "subtitle1" :color "textSecondary"} "Die Eltern dieses Vorschlags hat keine Argumente.")
        (for [parents parents-with-arguments]
          (ui-parent-argument-section parents {:selected-arguments selected-arguments
                                               :toggle-argument toggle-argument}))))))


(def ui-argument-step (comp/computed-factory ArgumentStep))
;; endregion

;; region Final Step
(defn- section [& children]
  (apply dd/typography {:variant "overline" :component "h3"} children))

(defn- quoted-body [& children]
  (apply dd/typography
    {:variant "body1"
     :paragraph true
     :style {:borderLeft "solid 1px black"
             :paddingLeft "1em"
             :whiteSpace "pre-line"}}
    children))
;; endregion

(defn goto-step* [new-proposal-form step]
  (assoc new-proposal-form
    :ui/step
    (case step
      :type 0
      :parents 1
      :details 2
      :arguments 3
      :final 4)))

(defmutation goto-step [{:keys [step]}]
  (action [{:keys [ref state]}]
    (swap! state update-in ref goto-step* step)))

(defmutation next-step [_]
  (action [{:keys [ref state]}]
    (swap! state update-in ref update :ui/step inc)))

;; region Type Step
(defn button-card
  "A very large, multiline button."
  [opts & children]
  (inputs/button
    (merge
      {:style {:height "100%"}
       :size "large"
       :variant "outlined"
       :fullWidth true}
      opts)
    (apply layout/box {:display "flex" :flexDirection "column"} children)))

(defsc TypeStep [_ _ {:keys [to-step]}]
  (comp/fragment
    (dd/typography {:paragraph true}
      "Möchtest du einen neuen Vorschlag hinzufügen, einen bestehenden erweitern oder Vorschläge zusammenführen?")
    (grid/container {:spacing 2}
      (grid/item {:sm 6 :xs 12}
        (button-card
          {:onClick #(to-step :details)
           :startIcon (comp/create-element AddBox nil nil)}
          "Neu"
          (dd/typography {:variant "caption" :color "textSecondary"}
            "Einen neuen Vorschlag erstellen, der nicht mit einem anderen verwandt ist.")))

      (grid/item {:sm 6 :xs 12}
        (button-card
          {:onClick #(to-step :parents)
           :startIcon (layout/box {:clone true :css {:transform "rotate(.5turn)"}}
                        (comp/create-element MergeType nil nil))}
          "Ableiten / Zusammenführen"
          (dd/typography {:variant "caption" :color "textSecondary"}
            "Ein Vorschlag erstellen mit einem oder mehreren Vorgängern."))))))

(def ui-type-step (comp/computed-factory TypeStep))
;; endregion

;; region Parent Step
(defmutation add-parent [{:keys [::proposal/ident]}]
  (action [{:keys [app state ref]}]
    (swap! state targeting/integrate-ident* ident :append (conj ref :parents))
    (load-parents-with-arguments! app ident)))

(defmutation remove-parent [{:keys [::proposal/ident]}]
  (action [{:keys [state ref]}]
    (norm/swap!-> state
      (mrg/remove-ident* ident (conj ref :parents)))))      ; TODO Remove Arguments as well

(>defn- id-in-parents? [parents id]
  [(s/coll-of map?) uuid? => boolean?]
  (contains? (set (map ::proposal/id parents)) id))

(defsc ProposalItem [_ {::proposal/keys [nice-id title]} {:keys [checked? onClick]}]
  {:query [::proposal/id ::proposal/nice-id ::proposal/title]
   :ident ::proposal/id
   :use-hooks true}
  (list/item {}
    (list/item-icon {}
      (inputs/checkbox
        {:edge :start
         :onClick onClick
         :checked checked?}))
    (list/item-text {} (str "#" nice-id " " title))))
(def ui-proposal-item (comp/computed-factory ProposalItem {:keyfn ::proposal/id}))

(defsc ProcessProposalList [_ {::process/keys [proposals]} {:keys [selected add-parent remove-parent]}]
  {:query [::process/slug {::process/proposals (comp/get-query ProposalItem)}]
   :ident ::process/slug
   :initial-state (fn [{:keys [slug]}] {::process/slug slug})}
  (list/list {:dense true}
    (for [{::proposal/keys [id] :as proposal} proposals
          :let [checked? (id-in-parents? selected id)]]
      (ui-proposal-item proposal {:checked? checked?
                                  :onClick #(if checked? (remove-parent [::proposal/id id])
                                                         (add-parent [::proposal/id id]))}))))

(def ui-process-proposal-list (comp/computed-factory ProcessProposalList))

(defsc ParentStep [_ {:keys [proposal-list]} {:keys [selected add-parent remove-parent next-step]}]
  {:query [{:proposal-list (comp/get-query ProcessProposalList)}]
   :initial-state
   (fn [{:keys [slug]}]
     {:proposal-list (comp/get-initial-state ProcessProposalList {:slug slug})})}
  (comp/fragment
    (dd/typography {:paragraph false}
      "Von welchen bestehenden Vorschlägen hängt dein neuer Vorschlag ab?")
    (dd/typography {:paragraph false :variant "caption"}
      "Du kannst einen oder mehrere wählen.")

    (ui-process-proposal-list proposal-list
      {:selected selected
       :add-parent add-parent
       :remove-parent remove-parent})

    (inputs/button
      {:color "primary"
       :onClick next-step}
      "Weiter")))

(def ui-parent-step (comp/computed-factory ParentStep))
;; endregion

(defmutation show [{:keys [slug title body parents]
                    :or {title "" body "" parents []}}]
  (action [{:keys [app state]}]
    (swap! state update-in [::NewProposalFormDialog slug]
      assoc
      :ui/open? true
      :ui/title title
      :ui/body body
      :parents parents
      :ui/step (if (empty? parents) 0 1))
    (apply load-parents-with-arguments! app parents)))


(defsc Parent [_ _]
  {:query [::proposal/id ::proposal/title ::proposal/body ::proposal/nice-id
           {::proposal/arguments (comp/get-query Argument)}]
   :ident ::proposal/id})

(defn new-body [parents]
  (case (count parents)
    0 ""
    1 (-> parents first ::proposal/body)
    ""))

(defn copy-parents-body* [form parents]
  (assoc form :ui/body (new-body parents)))

(defmutation copy-parents-body [_]
  (action [{:keys [state ref component]}]
    (let [{:keys [parents]} (norm/ui->props component)]
      (swap! state update-in ref copy-parents-body* parents))))

(defsc NewProposalFormDialog [this {:ui/keys [open? title body step]
                                    :step/keys [parent-step]
                                    :keys [parents arguments]
                                    ::process/keys [slug]}]
  {:query [::process/slug
           :ui/open?

           :ui/title :ui/body
           {:parents (comp/get-query Parent)}
           {:arguments (comp/get-query Argument)}

           :ui/step

           {:step/parent-step (comp/get-query ParentStep)}]
   :ident [::NewProposalFormDialog ::process/slug]
   :initial-state (fn [{:keys [slug]}]
                    (merge
                      {:parents []
                       :arguments #{}}
                      #:ui{:open? false
                           :title ""
                           :body ""
                           :step 0}
                      #:step{:parent-step (comp/get-initial-state ParentStep {:slug slug})}
                      {::process/slug slug}))
   :use-hooks? true}
  (let [close-dialog (hooks/use-callback #(m/set-value! this :ui/open? false) [])
        reset-form (hooks/use-callback #(comp/transact! this [(reset-form {})]) [])
        next-step (hooks/use-callback #(comp/transact! this [(next-step {})]) [])]
    (dialog/dialog
      {:open open?
       :fullWidth true
       :fullScreen (breakpoint/<=? "xs")
       :maxWidth "md"
       :onClose close-dialog
       :onExit reset-form
       :PaperProps {:component "form"
                    :onSubmit (fn submit-new-proposal-form [e]
                                (evt/prevent-default! e)
                                (comp/transact! this
                                  [(process/add-proposal
                                     {::process/slug slug
                                      ::proposal/id (tempid/tempid)
                                      ::proposal/title title
                                      ::proposal/body body
                                      ::proposal/parents (mapv #(select-keys % [::proposal/id]) parents)
                                      ::proposal/arguments (vec arguments)})])
                                (close-dialog))}}
      (dialog/title {} "Neuer Vorschlag")
      (dialog/content {}
        (stepper/stepper
          {:activeStep step
           :orientation "vertical"
           :nonLinear true}

          ;; region Typ Step
          (stepper/step
            {:completed (pos? step)}
            (stepper/step-button
              {:onClick #(comp/transact! this [(goto-step {:step :type})])}
              "Typ")
            (stepper/step-content {}
              (ui-type-step {} {:to-step (fn [step] (comp/transact! this [(goto-step {:step step})]))})))
          ;; endregion

          ;; region Parents Step
          (stepper/step
            {:completed (< 1 step)}
            (stepper/step-button
              {:onClick #(comp/transact! this [(goto-step {:step :parents})])
               :style {:textAlign "start"}
               :optional (dd/typography {:variant "caption" :color "textSecondary"} "Optional")}
              "Eltern")
            (stepper/step-content {}
              (ui-parent-step parent-step
                {:next-step next-step
                 :add-parent (fn [ident] (comp/transact! this [(add-parent {::proposal/ident ident})]))
                 :remove-parent (fn [ident] (comp/transact! this [(remove-parent {::proposal/ident ident})]))
                 :selected parents})))
          ;; endregion

          ;; region Details Step
          (let [change-title (hooks/use-callback (partial m/set-string!! this :ui/title :event) [])
                change-body (hooks/use-callback (partial m/set-string!! this :ui/body :event) [])
                error? (or
                         (str/blank? title)
                         (str/blank? body))]
            (stepper/step
              {:completed
               (and (< 2 step) (not error?))}
              (stepper/step-button
                {:onClick #(comp/transact! this [(goto-step {:step :details})])
                 :style {:textAlign "start"}                ; fix for text alignment in button
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
                  {:label "Details"
                   :variant "outlined"
                   :margin "normal"
                   :fullWidth true
                   :autoComplete "off"
                   :multiline true
                   :rows 7
                   :value body
                   :onChange change-body})
                (when (= 1 (count parents))
                  (inputs/button {:size :small :variant :outlined
                                  :startIcon (dom/create-element FileCopy)
                                  :onClick #(comp/transact! this [(copy-parents-body nil)])}
                    "Copy details from parent"))
                (dom/br {})

                (inputs/button
                  {:color "primary"
                   :variant "contained"
                   :disabled error?
                   :onClick #(comp/transact! this [(goto-step {:step (if (empty? parents) :final :arguments)})])}
                  "Weiter"))))
          ;; endregion

          ;; region Arguments Step
          (stepper/step
            {:disabled  (empty? parents)
             :completed (< 3 step)}
            (stepper/step-button
              {:onClick #(comp/transact! this [(goto-step {:step :arguments})])
               :style {:textAlign "start"}
               :optional (dd/typography {:variant "caption" :color "textSecondary"}
                           (if (empty? parents)
                             "Nur mit Eltern möglich."
                             "Optional"))}
              "Argumente")
            (stepper/step-content {}
              (ui-argument-step {}
                {:parents parents
                 :selected-arguments arguments
                 :toggle-argument #(comp/transact! this [(toggle-argument {::argument/ident %})])})
              (inputs/button
                {:color "primary"
                 :onClick next-step}
                "Weiter")))
          ;; endregion

          (stepper/step {}
            (stepper/step-button
              {:onClick #(comp/transact! this [(goto-step {:step :final})])}
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
                    (list/list {:dense true :disablePadding true}
                      (for [{::proposal/keys [nice-id title]} parents]
                        (list/item {:key nice-id}
                          (list/item-icon {} (dom/span (str "#" nice-id)))
                          (list/item-text {} (str title))))))))

              (inputs/button {:color "primary"
                              :type "submit"
                              :variant "contained"}
                "Abschicken")))))


      (dialog/actions {}
        (inputs/button {:onClick close-dialog} "Abbrechen")))))

(def ui-new-proposal-form (comp/computed-factory NewProposalFormDialog))