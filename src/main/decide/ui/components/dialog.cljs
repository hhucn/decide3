(ns decide.ui.components.dialog
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [mui.feedback.dialog :as dialog]))

(defmutation open [{:keys [id]}]
  (action [{:keys [state]}]
    (swap! state update-in [:dialog/id id] assoc :ui/open? true)))

(defmutation close [{:keys [id]}]
  (action [{:keys [state]}]
    (swap! state update-in [:dialog/id id] assoc :ui/open? false)))

(defn close! [this id]
  (comp/transact! this [(close {:id id})]
    {:compressible? true}))

(defmutation toggle [{:keys [id]}]
  (action [{:keys [state]}]
    (swap! state update-in [:dialog/id id] update :ui/open? not)))

(defn toggle! [this id]
  (comp/transact! this [(toggle {:id id})]
    {:compressible? true
     :refresh [:dialog/id id]}))

(defsc Dialog [this
               {:keys [dialog/id ui/open?]}
               {:keys [dialogProps]}]
  {:query [:dialog/id :ui/open?]
   :ident :dialog/id
   :initial-state {:dialog/id :param/id
                   :ui/open? false}}
  (apply dialog/dialog
    (merge
      dialogProps
      {:open open?
       :onClose #(close! this id)})
    (comp/children this)))

(def ui-dialog (comp/computed-factory Dialog {:keyfn :dialog/id}))