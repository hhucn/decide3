(ns decide.ui.proposal.NewProposal
  (:require
    [com.fulcrologic.fulcro.algorithms.merge :as mrg]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [clojure.tools.reader.edn :as edn]
    [decide.models.proposal :as proposal]
    [decide.utils :as utils]
    [material-ui.data-display :as dd]
    [material-ui.feedback :as feedback]
    [material-ui.inputs :as inputs]
    [material-ui.layout :as layout]
    [material-ui.navigation :as navigation]
    ["@material-ui/icons/RemoveCircleOutline" :default RemoveIcon]))

(defsc Parent [_this {::proposal/keys [id title]} {:keys [onDelete]}]
  {:query [::proposal/id ::proposal/title]}
  (dd/list-item {}
    (dd/list-item-avatar {} (dom/span (str "#" id)))
    (dd/list-item-text {} (str title))
    (dd/list-item-secondary-action {}
      (inputs/icon-button
        {:onClick    onDelete
         :aria-label "Elternteil entfernen"}
        (comp/create-element RemoveIcon #js {:color "error"} nil)))))

(def ui-parent (comp/computed-factory Parent {:keyfn ::proposal/id}))

;;; region Mutations
(defmutation add-parent [{:keys [parent/ident]}]
  (action [{:keys [state ref]}]
    (swap! state targeting/integrate-ident* ident :append (conj ref :ui/parents))))

(defmutation remove-parent [{:keys [parent/ident]}]
  (action [{:keys [state ref]}]
    (swap! state mrg/remove-ident* ident (conj ref :ui/parents))))

(defn- id-in-parents? [parents id]
  ((set (map ::proposal/id parents)) id))

(defmutation show-new-proposal-form-dialog [{:keys [title body parents]
                                             :or   {title "" body "" parents []}}]
  (action [{:keys [state]}]
    (swap! state update-in [:component :new-proposal]
      assoc
      :ui/open? true
      :ui/title title
      :ui/body body
      :ui/parents parents)))

(defmutation reset-form [_]
  (action [{:keys [state]}]
    (swap! state update-in [:component :new-proposal]
      assoc
      :ui/title ""
      :ui/body ""
      :ui/parents [])))
;;; endregion

(defsc NewProposalFormDialog [this {:ui/keys [open? title body parents] :keys [all-proposals]}]
  {:query         [:ui/open?
                   :ui/title :ui/body
                   {:ui/parents (comp/get-query Parent)}
                   {[:all-proposals '_] (comp/get-query Parent)}]
   :ident         (fn [] [:component :new-proposal])
   :initial-state #:ui{:open?   false
                       :title   ""
                       :body    ""
                       :parents []}
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
                                   [(proposal/add-proposal
                                      {::proposal/id      (tempid/tempid)
                                       ::proposal/title   title
                                       ::proposal/body    body
                                       ::proposal/parents (mapv #(select-keys % [::proposal/id]) parents)})])
                                 (close-dialog))}}
      (feedback/dialog-title {} "Neuer Vorschlag")
      (let [change-title (hooks/use-callback (partial m/set-string! this :ui/title :event))
            change-body (hooks/use-callback (partial m/set-string! this :ui/body :event))]
        (feedback/dialog-content {}
          (inputs/textfield
            {:label        "Titel"
             :variant      "filled"
             :fullWidth    true
             :autoComplete "off"
             :value        title
             :onChange     change-title})
          (layout/box {:my 1}
            (dd/typography {:component "h3"} "Eltern")
            (dd/list {}
              (for [{::proposal/keys [id] :as props} parents
                    :let [delete-parent #(comp/transact! this [(remove-parent {:parent/ident [::proposal/id id]})])]]
                (ui-parent props
                  {:onDelete delete-parent}))
              (inputs/textfield {:label     "Eltern hinzufügen"
                                 :variant   "filled"
                                 :margin    "normal"
                                 :select    true
                                 :value     ""
                                 :fullWidth true
                                 :onChange  #(comp/transact! this [(add-parent {:parent/ident (edn/read-string (evt/target-value %))})])}
                (for [{::proposal/keys [id title]} all-proposals
                      :when (not (id-in-parents? parents id))]
                  (navigation/menu-item {:key id :value (str [::proposal/id id])}
                    (str "#" id " " title))))))
          (inputs/textfield
            {:label        "Details"
             :variant      "filled"
             :margin       "normal"
             :fullWidth    true
             :autoComplete "off"
             :multiline    true
             :rows         7
             :value        body
             :onChange     change-body})))
      (feedback/dialog-actions {}
        (inputs/button {:color "primary" :onClick close-dialog} "Abbrechen")
        (inputs/button {:color "primary"
                        :type  "submit"} "Absenden")))))

(def ui-new-proposal-form2 (comp/computed-factory NewProposalFormDialog))