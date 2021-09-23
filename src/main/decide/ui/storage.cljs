(ns decide.ui.storage
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]))

(def localstorage-key ::local)

(defn- set-item!
  "Set `key' in browser's localStorage to `val`."
  [key val]
  (.setItem (.-localStorage js/window) (name key) val))

(defn- get-item
  "Returns value of `key' from browser's localStorage."
  [key]
  (.getItem (.-localStorage js/window) (name key)))

(defn- remove-item!
  "Remove the browser's localStorage value for the given `key`"
  [key]
  (.removeItem (.-localStorage js/window) key))

(defn clear! [] (.clear (.-localStorage js/window)))

(defmutation set-item [{:keys [key value]}]
  (action [{:keys [state]}]
    (set-item! key value)
    (swap! state assoc-in [localstorage-key key] value)))

(defmutation remove-item [{:keys [key]}]
  (action [{:keys [state]}]
    (remove-item! key)
    (swap! state update localstorage-key dissoc key)))

(defmutation clear [_]
  (action [{:keys [state]}]
    (clear!)
    (swap! state assoc localstorage-key {})))

(defn- get-localstorage []
  (into {}
    (map (fn [[k v]] [(keyword k) v]))
    (js->clj (js/Object.entries (.-localStorage js/window)))))

(defsc LocalStorage [_ _]
  {:query ['*]
   :initial-state (fn [_] (get-localstorage))
   :componentDidMount
   (fn [this]
     (.addEventListener js/window "storage"
       (fn [e]
         (let [key (keyword (.-key e))
               new-value (.-newValue e)]
           (comp/transact! this
             [(cond
                (nil? key) (clear nil)
                (nil? new-value) (remove-item {:key key})
                :else (set-item {:key key :value new-value}))])))))})

(def ui-localstorage (comp/factory LocalStorage))