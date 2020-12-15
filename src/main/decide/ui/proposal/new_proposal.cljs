(ns decide.ui.proposal.new-proposal
  (:require
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
    ["@material-ui/icons/RemoveCircleOutline" :default RemoveIcon]))



(defsc Argument [this {::argument/keys       [content]
                       :ui.new-proposal/keys [checked?]
                       :or                   {checked? false}}]
  {:query [::argument/id ::argument/content :ui.new-proposal/checked?]
   :ident ::argument/id}
  (dd/list-item {}
    (dd/list-item-icon {}
      (inputs/checkbox
        {:checked checked?
         :onClick #(m/toggle! this :ui.new-proposal/checked?)}))
    (dd/list-item-text {} content)))

(def ui-argument (comp/computed-factory Argument {:keyfn ::argument/id}))

(defsc ParentArgumentSection [this {::proposal/keys [id title arguments]}]
  {:query [::proposal/id ::proposal/title
           {::proposal/arguments (comp/get-query Argument)}]
   :ident ::proposal/id}
  (when-not (empty? arguments)
    (dd/list
      {:dense     true
       :subheader (dd/list-subheader {} title)}
      (map ui-argument arguments))))

(def ui-parent-argument-section (comp/computed-factory ParentArgumentSection {:keyfn ::proposal/id}))

(defn load-parents-with-arguments! [app-or-comp & parent-idents]
  (run!
    #(df/load! app-or-comp % ParentArgumentSection
       {:parallel true
        :focus    [::proposal/arguments]})
    parent-idents))

(defsc ParentListItem [_this {::proposal/keys [id title]} {:keys [onDelete]}]
  {:query [::proposal/id ::proposal/title
           {::proposal/arguments (comp/get-query Argument)}]}
  (dd/list-item {}
    (dd/list-item-icon {} (dom/span (str "#" id)))
    (dd/list-item-text {} (str title))
    (dd/list-item-secondary-action {}
      (inputs/icon-button
        {:onClick onDelete
         :title   "Elternteil entfernen"}
        (comp/create-element RemoveIcon #js {:color "error"} nil)))))

(def ui-parent-list-item (comp/computed-factory ParentListItem {:keyfn ::proposal/id}))

;;; region Mutations
(defmutation add-parent [{:keys [parent/ident]}]
  (action [{:keys [app state ref]}]
    (swap! state targeting/integrate-ident* ident :append (conj ref :ui/parents))
    (load-parents-with-arguments! app ident)))

(defn- remove-checked-from-all-arguments [state]
  (update state ::argument/id #(into {} (map (fn [[k v]] [k (dissoc v :ui.new-proposal/checked?)])) %)))

(defmutation remove-parent [{:keys [parent/ident]}]
  (action [{:keys [state ref]}]
    (norm/swap!-> state
      (mrg/remove-ident* ident (conj ref :ui/parents))
      remove-checked-from-all-arguments)))

(defn- id-in-parents? [parents id]
  ((set (map ::proposal/id parents)) id))

(defmutation show [{:keys [id title body parents]
                    :or   {title "" body "" parents []}}]
  (action [{:keys [app state]}]
    (swap! state update-in [::process/slug id]
      assoc
      :ui/open? true
      :ui/title title
      :ui/body body
      :ui/parents parents)
    (apply load-parents-with-arguments! app parents)))

(defmutation reset-form [_]
  (action [{:keys [state ref]}]
    (norm/swap!-> state
      (update-in ref
        assoc
        :ui/title ""
        :ui/body ""
        :ui/parents [])
      remove-checked-from-all-arguments)))
;;; endregion

(defn get-argument-idents-from-proposal [{::proposal/keys [arguments]}]
  (->> arguments
    (filter :ui.new-proposal/checked?)
    (map (partial comp/get-ident Argument))))

(defn- parent-selection [*this {:ui/keys       [parents]
                                ::process/keys [proposals]}]
  ; TODO this is ugly ui-wise
  (layout/box {:my 1}
    (dd/typography {:component "h3"} "Eltern")
    (dd/list {}
      (for [{::proposal/keys [id] :as props} parents
            :let [delete-parent #(comp/transact! *this [(remove-parent {:parent/ident [::proposal/id id]})])]]
        (ui-parent-list-item props
          {:onDelete delete-parent}))
      (inputs/textfield {:label     "Eltern hinzufügen"
                         :variant   "filled"
                         :margin    "normal"
                         :select    true
                         :value     ""
                         :fullWidth true
                         :onChange  #(comp/transact! *this [(add-parent {:parent/ident (edn/read-string (evt/target-value %))})])}
        (for [{::proposal/keys [id title]} proposals
              :when (not (id-in-parents? parents id))
              :let [proposa-ident [::proposal/id id]]]
          (navigation/menu-item {:key id :value (str proposa-ident)}
            (str "#" id " " title)))))))

(defn- argument-selection [*this {:ui/keys [parents]}]
  (layout/box {:border 0 :borderColor "grey.700"}
    (dd/typography {:component "h3"} "Welche Argumente sind immer noch relevant?")
    (map ui-parent-argument-section parents)))

(defsc NewProposalFormDialog [this {:ui/keys       [open? title body parents]
                                    ::process/keys [slug proposals] :as props}]
  {:query         [::process/slug
                   :ui/open?
                   :ui/title :ui/body
                   {:ui/parents (comp/get-query ParentListItem)}
                   {::process/proposals (comp/get-query ParentListItem)}]
   :ident         ::process/slug
   :initial-state (fn [{:keys [id]}]
                    (merge
                      #:ui{:open?   false
                           :title   ""
                           :body    ""
                           :parents []}
                      {::process/slug id}))
   :use-hooks?    true}
  (let [close-dialog (hooks/use-callback #(m/set-value! this :ui/open? false))
        reset-form (hooks/use-callback #(comp/transact! this [(reset-form {})] {:compressible? true}))]
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
      (let [change-title (hooks/use-callback (partial m/set-string! this :ui/title :event))
            change-body (hooks/use-callback (partial m/set-string! this :ui/body :event))]
        (feedback/dialog-content {}

          ;; Title
          (inputs/textfield
            {:label        "Titel"
             :variant      "filled"
             :fullWidth    true
             :autoComplete "off"
             :value        title
             :onChange     change-title})

          (parent-selection this props)

          ;; Body
          (inputs/textfield
            {:label        "Details"
             :variant      "filled"
             :margin       "normal"
             :fullWidth    true
             :autoComplete "off"
             :multiline    true
             :rows         7
             :value        body
             :onChange     change-body})

          ;; Argument selection
          (when-not (empty? parents)
            (argument-selection this props))))

      (feedback/dialog-actions {}
        (inputs/button {:color "primary" :onClick close-dialog} "Abbrechen")
        (inputs/button {:color "primary"
                        :type  "submit"} "Absenden")))))

(def ui-new-proposal-form (comp/computed-factory NewProposalFormDialog))