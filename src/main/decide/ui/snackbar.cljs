(ns decide.ui.snackbar
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :refer [defmutation]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [decide.application :refer [SPA]]
    [material-ui.feedback :as feedback]
    [material-ui.inputs :as inputs]
    [taoensso.timbre :as log]
    ["@material-ui/icons/Close" :default Close]
    [material-ui.surfaces :as surfaces]))

(def ident [:component :snackbars])

(defn- -next-snackbar [snackbar-container]
  (assoc snackbar-container
    :current (first (:next snackbar-container))
    :next (vec (rest (:next snackbar-container)))))

(defmutation next-snackbar [_]
  (action [{:keys [state]}]
    (swap! state update-in ident -next-snackbar)))

(defmutation close-first-snackbar [_]
  (action [{:keys [state]}]
    (swap! state
      update-in ident
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
       #(comp/transact! this [(next-snackbar nil)] {:only-refresh [ident]})
       :message message
       :action (inputs/icon-button
                 {:size "small"
                  :color "inherit"
                  :onClick handle-close
                  :ariaLabel "close"}
                 (comp/create-element Close #js {:fontSize "small"} nil))})))

(def ui-snackbar (comp/computed-factory Snackbar))

(defmutation add-snackbar [{:keys [message]}]
  (action [{:keys [state]}]
    (swap! state update-in ident -add-snackbar (comp/get-initial-state Snackbar {:message message}))))

(defn add-snackbar!
  "Queues a new snackbar to show.
  Accepts a map like:
  ```
  {:message \"Hello World\"}
  ```"
  [{:keys [message]}]
  (comp/transact! SPA [(add-snackbar {:message message})] {:only-refresh [ident]})
  nil)

(defsc SnackbarContainer [_this {:keys [current]}]
  {:query [{:current (comp/get-query Snackbar)}
           {:next (comp/get-query Snackbar)}]
   :ident (fn [] ident)
   :initial-state {:next []}
   :use-hooks? true}
  (when current
    (log/info (:id current) "current")
    (ui-snackbar current)))

(def ui-snackbar-container (comp/factory SnackbarContainer))
