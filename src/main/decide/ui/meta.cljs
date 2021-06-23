(ns decide.ui.meta
  "Tools to set meta data for the HTML document, like title, language,... "
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]))

(def root-key ::meta)

(defmutation set-meta [props]
  (action [{:keys [state]}]
    (swap! state
      update root-key
      merge props)))

(defn set-title [title]
  (set-meta {:title title}))

(defsc Meta [_ _]
  {:query [:title :lang]
   :initial-state
   (fn [params]
     (merge
       {:title (.-title js/document)
        :lang (.. js/document -documentElement -lang)}
       params))
   :shouldComponentUpdate
   (fn [_this {:keys [title lang]}]
     (when-not (= title (.-title js/document))
       (set! (.-title js/document) title))
     (when-not (= lang (.. js/document -documentElement -lang))
       (set! (.. js/document -documentElement -lang) lang))
     false)})

(def ui-meta (comp/factory Meta))