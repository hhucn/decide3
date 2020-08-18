(ns decide.ui.components.NewProposal
  (:require [material-ui.layout :as layout]
            [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
            [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom :refer [div]]
            [com.fulcrologic.fulcro.algorithms.merge :as mrg]
            [decide.utils :as utils]
            [material-ui.inputs :as inputs]
            [material-ui.data-display :as dd]
            [decide.models.proposal :as proposal]
            [taoensso.timbre :as log]
            [material-ui.feedback :as feedback]
            ["@material-ui/icons/RemoveCircleOutline" :default RemoveIcon]
            ["react" :as React]
            [com.fulcrologic.fulcro.dom.events :as evt]
            [material-ui.navigation :as navigation]
            [clojure.tools.reader.edn :as edn]
            [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]))

(defmutation add-new-proposal [{:proposal/keys [id title body parents] :as params}]
  (action [{:keys [app state]}]
    (log/info params)
    (mrg/merge-component! app proposal/Proposal params :append [:all-proposals]))
  (remote [env]
    (m/with-server-side-mutation env 'decide.api.proposal/add-proposal)))

(defmutation remove-parent [{:keys [parent/ident]}]
  (action [{:keys [state ref]}]
    (swap! state mrg/remove-ident* ident (conj ref :ui/parents))))

(defsc Parent [this {:proposal/keys [id title]} {:keys [onDelete]}]
  {:query [:proposal/id :proposal/title]}
  (dd/list-item {}
    (dd/list-item-avatar {} (str "#" id))
    (dd/list-item-text {} (str title))
    (dd/list-item-secondary-action {}
      (inputs/icon-button
        {:onClick    onDelete
         :aria-label "Elternteil entfernen"}
        (React/createElement RemoveIcon #js {:color "error"})))))

(def ui-parent (comp/computed-factory Parent {:keyfn :proposal/id}))

(defmutation add-parent [{:keys [parent/ident]}]
  (action [{:keys [state ref]}]
    (swap! state targeting/integrate-ident* ident :append (conj ref :ui/parents))))

(defn- id-in-parents? [parents id]
  ((set (map :proposal/id parents)) id))

(defsc NewProposalForm [this {:ui/keys [title body parents adding-parent?] :keys [all-proposals]} {:keys [onClose]}]
  {:query         [:ui/title :ui/body {:ui/parents (comp/get-query Parent)}
                   :ui/adding-parent?
                   {[:all-proposals '_] (comp/get-query Parent)}]
   :ident         (fn [] [:component :new-proposal])
   :initial-state #:ui{:title "" :body "" :parents [] :adding-parent? false}}
  (dom/form
    {:id :new-proposal-dialog-form
     :onSubmit (fn submit-new-proposal-form [e]
                 (evt/prevent-default! e)
                 (log/info "New proposal added")
                 (comp/transact! this
                   [(add-new-proposal #:proposal{:id 1000
                                                 :title title
                                                 :body body
                                                 :parents (mapv #(select-keys % [:proposal/id]) parents)})
                    (m/set-props (comp/get-initial-state NewProposalForm))])
                 (onClose))}
    (inputs/textfield
      {:label        "Titel"
       :variant      "filled"
       :fullWidth    true
       :autoComplete "off"
       :value        title
       :onChange     #(m/set-string! this :ui/title :event %)})
    (layout/box {:my 1}
      (dd/typography {:component "h3"} "Eltern")
      (dd/list {}
        (for [{:proposal/keys [id] :as props} parents]
          (ui-parent props
            {:onDelete #(comp/transact! this [(remove-parent {:parent/ident [:proposal/id id]})])}))
        (inputs/textfield {:label     "Eltern hinzufügen"
                           :variant   "filled"
                           :margin    "normal"
                           :select    true
                           :value     ""
                           :fullWidth true
                           :onChange  #(comp/transact! this [(add-parent {:parent/ident (edn/read-string (evt/target-value %))})])}
          (for [{:proposal/keys [id title]} all-proposals
                :when (not (id-in-parents? parents id))]
            (navigation/menu-item {:key id :value (str [:proposal/id id])}
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
       :onChange     #(m/set-string! this :ui/body :event %)})))

(def ui-new-proposal-form (comp/computed-factory NewProposalForm))

(defn new-proposal-dialog
  [{:keys [open? onClose] :or {open? false}}
   form]
  (feedback/dialog
    {:open       open?
     :fullWidth  true
     :fullScreen (utils/<=-breakpoint? "xs")
     :maxWidth   "md"
     :onClose    onClose}
    (feedback/dialog-title {} "Neuer Vorschlag")
    (feedback/dialog-content {} form)
    (feedback/dialog-actions {}
      (inputs/button {:color "primary" :onClick onClose} "Abbrechen")
      (inputs/button {:color "primary"
                      :form  :new-proposal-dialog-form
                      :type  "submit"} "Absenden"))))