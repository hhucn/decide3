(ns decide.ui.meta
  "Tools to set meta data for the HTML document, like title, language,... "
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]))

(def root-key ::meta)

(defmutation set-meta [{:keys [title]}]
  (action [{:keys [state]}]
    (swap! state
      update root-key ; swap this out for an ident in meta?
      assoc ::title title)))

(defn set-title [title]
  (set-meta {:title title}))

(defsc Meta [_ _]
  {:query [::title]
   :initial-state {::title "decide"}
   :shouldComponentUpdate
   (fn [this]
     (let [{::keys [title]} (comp/props this)]
       (when-not (= title (.-title js/document))
         (set! (.-title js/document) title)))
     false)})

(def ui-meta (comp/factory Meta))