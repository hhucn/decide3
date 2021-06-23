(ns decide.ui.components.snackbar
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.mutations :refer [defmutation]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [decide.application :refer [SPA]]
    [material-ui.feedback :as feedback]
    [material-ui.inputs :as inputs]
    ["@material-ui/icons/Close" :default Close]))

(def container-ident [:component/id ::Snackbar])

(defn- -next-snackbar [snackbar-container]
  (assoc snackbar-container
    :current (first (:next snackbar-container))
    :next (vec (rest (:next snackbar-container)))))

(defmutation next-snackbar [_]
  (action [{:keys [state]}]
    (swap! state update-in container-ident -next-snackbar)))

(defmutation close-first-snackbar [_]
  (action [{:keys [state]}]
    (swap! state
      update-in container-ident
      update :current
      assoc :open? false)))

(defn- -add-snackbar [snackbar-container snackbar]
  (if (:current snackbar-container)
    (-> snackbar-container
      (update :next conj snackbar)
      (assoc-in [:current :open?] false)) ; Remove this line if you want each snackbar to be displayed in full.
    (assoc snackbar-container :current snackbar)))

(defsc Snackbar [this {:keys [message open?]}]
  {:query [:message :open?]
   :initial-state (fn [{:keys [message]}]
                    {:message message
                     :open? true})
   :use-hooks? true}
  (let [handle-close
        (hooks/use-callback
          (fn handle-close [_ reason]
            (when (not= reason "clickaway")
              (comp/transact! this [(close-first-snackbar {})]) [])))]
    (feedback/snackbar
      {:anchorOrigin {:vertical "bottom" :horizontal "center"}
       :open open?
       :onClose handle-close
       :autoHideDuration 6000
       :onExited
       #(comp/transact! this [(next-snackbar {})] {:only-refresh [container-ident]})
       :message message
       :action (inputs/icon-button
                 {:size "small"
                  :color "inherit"
                  :onClick handle-close
                  :aria-label (i18n/trc "[aria]" "Close")}
                 (dom/create-element Close #js {:fontSize "small"}))})))

(def ui-snackbar (comp/computed-factory Snackbar))

(defmutation add [{:keys [message]}]
  (action [{:keys [state]}]
    (swap! state update-in container-ident -add-snackbar (comp/get-initial-state Snackbar {:message message}))))

(defn add!
  "Queues a new snackbar to show.
  Accepts a map like:
  ```
  {:message \"Hello World\"}
  ```"
  [{:keys [message]}]
  (comp/transact! SPA [(add {:message message})] {:only-refresh [container-ident]}))

(defsc SnackbarContainer [_this {:keys [current]}]
  {:query [{:current (comp/get-query Snackbar)}
           {:next (comp/get-query Snackbar)}]
   :ident (fn [] container-ident)
   :initial-state (fn [_] {:next [] :current nil})
   :use-hooks? true}
  (when current
    (ui-snackbar current)))

(def ui-snackbar-container (comp/factory SnackbarContainer))
