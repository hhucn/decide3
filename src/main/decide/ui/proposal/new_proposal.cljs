(ns decide.ui.proposal.new-proposal
  (:require
    [cljs.spec.alpha :as s]
    [clojure.string :as str]
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
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
    [decide.models.process :as process]
    [decide.models.process.mutations :as process.mutations]
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
      :final 3)))

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
      (i18n/tr "Do you want to add a new proposal, fork from an existing or merge proposals?"))
    (grid/container {:spacing 2}
      (grid/item {:sm 6 :xs 12}
        (button-card
          {:onClick #(to-step :details)
           :startIcon (comp/create-element AddBox nil nil)}
          (i18n/tr "New")
          (dd/typography {:variant "caption" :color "textSecondary"}
            (i18n/tr "Create a new proposal that is not related to another one."))))

      (grid/item {:sm 6 :xs 12}
        (button-card
          {:onClick #(to-step :parents)
           :startIcon (layout/box {:clone true :css {:transform "rotate(.5turn)"}}
                        (comp/create-element MergeType nil nil))}
          (i18n/tr "Fork / Merge")
          (dd/typography {:variant "caption" :color "textSecondary"}
            (i18n/tr "Create a proposal derived from one or more proposals")))))))

(def ui-type-step (comp/computed-factory TypeStep))
;; endregion

;; region Parent Step
(defmutation add-parent [{:keys [::proposal/ident]}]
  (action [{:keys [state ref]}]
    (swap! state norm/integrate-ident ident :append (conj ref :parents))))

(defmutation remove-parent [{:keys [::proposal/ident]}]
  (action [{:keys [state ref]}]
    (norm/swap!-> state
      (norm/remove-ident ident (conj ref :parents)))))

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

(defsc ParentStep [_ {:keys [ui/current-process]} {:keys [selected add-parent remove-parent next-step]}]
  {:query [{[:ui/current-process '_] (comp/get-query ProcessProposalList)}]
   :initial-state {}}
  (comp/fragment
    (dd/typography {:paragraph false}
      (i18n/tr "Von welchen bestehenden Vorschlägen hängt dein neuer Vorschlag ab?"))
    (dd/typography {:paragraph false :variant "caption"}
      (i18n/tr "You can choose as many as you want"))

    (ui-process-proposal-list current-process
      {:selected selected
       :add-parent add-parent
       :remove-parent remove-parent})

    (inputs/button
      {:color "primary"
       :onClick next-step}
      "Weiter")))

(def ui-parent-step (comp/computed-factory ParentStep))
;; endregion

(defmutation show [{:keys [title body parents]
                    :or {title "" body "" parents []}}]
  (action [{:keys [app state]}]
    (when-let [ident (get @state :ui/current-process)]
      (df/load! app ident ProcessProposalList {:parallel true}))
    (swap! state update-in [:component/id ::NewProposalFormDialog]
      assoc
      :step/parent-step {}
      :ui/open? true
      :ui/title title
      :ui/body body
      :parents parents
      :ui/step (if (empty? parents) 0 1))))


(defsc Parent [_ _]
  {:query [::proposal/id ::proposal/title ::proposal/body ::proposal/nice-id]
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

(defsc NewProposalFormDialog [this {:ui/keys [open? title body step current-process]
                                    :step/keys [parent-step]
                                    :keys [parents]}]
  {:query [::process/slug
           :ui/open?

           :ui/title :ui/body
           {:parents (comp/get-query Parent)}

           :ui/step

           {:step/parent-step (comp/get-query ParentStep)}

           [:ui/current-process '_]]
   :ident (fn [] [:component/id ::NewProposalFormDialog])
   :initial-state (fn [{:keys [_slug]}]
                    (merge
                      {:parents []}
                      #:ui{:open? false
                           :title ""
                           :body ""
                           :step 0}
                      #:step{:parent-step (comp/get-initial-state ParentStep)}))
   :use-hooks? true}
  (let [[_ slug] current-process
        close-dialog (hooks/use-callback #(m/set-value! this :ui/open? false) [])
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
                                  [(process.mutations/add-proposal
                                     {::process/slug slug
                                      ::proposal/id (tempid/tempid)
                                      ::proposal/title title
                                      ::proposal/body body
                                      ::proposal/parents (mapv #(select-keys % [::proposal/id]) parents)})])
                                (close-dialog))}}
      (dialog/title {} (i18n/trc "Title of new proposal form" "New proposal"))
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
              (i18n/trc "Type of proposal" "Type"))
            (stepper/step-content {}
              (ui-type-step {} {:to-step (fn [step] (comp/transact! this [(goto-step {:step step})]))})))
          ;; endregion

          ;; region Parents Step
          (stepper/step
            {:completed (< 1 step)}
            (stepper/step-button
              {:onClick #(comp/transact! this [(goto-step {:step :parents})])
               :style {:textAlign "start"}
               :optional (dd/typography {:variant "caption" :color "textSecondary"} (i18n/trc "Input is optional" "Optional"))}
              (i18n/trc "Parents of a proposal" "Parents"))
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
                               (i18n/tr "Title and details must not be empty")))}
                (stepper/step-label
                  {:error (and (< 2 step) error?)}
                  (i18n/trc "Details of a proposal" "Details")))

              (stepper/step-content {}
                (dd/typography {:paragraph false}
                  (i18n/tr "Add a recognizable title"))
                (inputs/textfield
                  {:label (i18n/trc "Title of a proposal" "Title")
                   :variant "outlined"
                   :fullWidth true
                   :autoComplete "off"
                   :value title
                   :onChange change-title
                   :margin "normal"})

                ;; Body
                (inputs/textfield
                  {:label (i18n/trc "Details of a proposal" "Details")
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
                    (i18n/tr "Copy details from parent")))
                (dom/br {})

                (inputs/button
                  {:color "primary"
                   :variant "contained"
                   :disabled error?
                   :onClick #(comp/transact! this [(goto-step {:step :final})])}
                  (i18n/trc "Go to the next part of the form" "Next")))))
          ;; endregion

          (stepper/step {}
            (stepper/step-button
              {:onClick #(comp/transact! this [(goto-step {:step :final})])}
              (i18n/tr "Overview"))
            (stepper/step-content {}
              (layout/box {:mb 2}
                (section (str (i18n/trc "Title of a proposal" "Title") ":"))
                (quoted-body title)

                (section (str (i18n/trc "Details of a proposal" "Details") ":"))
                (quoted-body body)

                (when-not (empty? parents)
                  (comp/fragment
                    (section
                      (i18n/trf
                        "Derived from {count} other proposals:"
                        {:count (count parents)}))
                    (list/list {:dense true :disablePadding true}
                      (for [{::proposal/keys [nice-id title]} parents]
                        (list/item {:key nice-id}
                          (list/item-icon {} (dom/span (str "#" nice-id)))
                          (list/item-text {} (str title))))))))

              (inputs/button {:color "primary"
                              :type "submit"
                              :variant "contained"}
                (i18n/trc "Submit form" "Submit"))))))


      (dialog/actions {}
        (inputs/button {:onClick close-dialog} (i18n/trc "Abort form" "Cancel"))))))

(def ui-new-proposal-form (comp/computed-factory NewProposalFormDialog))