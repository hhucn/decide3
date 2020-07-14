(ns decide.models.todo
  (:require [com.fulcrologic.fulcro.dom :as dom :refer [div ul li p h1 h3 form button input span]]
            [com.fulcrologic.fulcro.dom.events :as evt]
            [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [com.fulcrologic.fulcro.algorithms.merge :as mrg]
            [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
            [com.fulcrologic.fulcro.algorithms.normalized-state :as norm-state]
            [clojure.string :as str]
            [material-ui.data-display :as dd]
            [material-ui.surfaces :as surfaces]
            [material-ui.inputs :as mui-input]
            [decide.ui.themes :as themes]
            [material-ui.inputs :as inputs]
            [material-ui.layout :as layout]
            [com.fulcrologic.fulcro-css.css :as css]
            ["@material-ui/icons/ExpandMore" :default ExpandMoreIcon]
            ["react" :as React]))

; region mutations

(defmutation delete-todo [{:keys [todo/id]}]
  (action [{:keys [state]}]
    (swap! state norm-state/remove-entity [:todo/id id]))
  (remote [env]
    (m/with-server-side-mutation env 'decide.api.todo/delete-todo)))

(defmutation update-todo-done [{:todo/keys [id done?]}]
  (action [{:keys [state]}]
    (swap! state assoc-in [:todo/id id :todo/done?] done?))
  (remote [env]
    (m/with-server-side-mutation env 'decide.api.todo/toggle-todo)))

(defmutation edit-todo [{:todo/keys [id task]}]
  (remote [env] (m/with-server-side-mutation env 'decide.api.todo/edit-todo)))
;endregion

(defsc Todo [this {:todo/keys [id task done? tags] :keys [ui/editing?]}]
  {:query     [:todo/id :todo/task :todo/done? :todo/tags
               :ui/editing?]
   :ident     :todo/id
   :pre-merge (fn [{:keys [data-tree _current-normalized _state-map _query]}] (merge {:ui/editing? false} data-tree))
   :css       [[:.chip {:margin ((get themes/shared :spacing) 0.5 "")}]
               [:.chip-array {:display    "flex"
                              :list-style "none"
                              :flex-wrap  "wrap"
                              :margin     0
                              :padding    ((:spacing themes/shared) 0.5 "")}]]}
  (let [{:keys [chip chip-array]} (css/get-classnames Todo)]
    (surfaces/expansion-panel {}
      (surfaces/expansion-panel-summary
        {:expandIcon (React/createElement ExpandMoreIcon)}
        (dd/list-item
          {:data-done done?}
          (dd/list-item-icon nil
            (mui-input/checkbox
              {:edge    :start
               :color   :primary
               :checked done?
               :onClick (fn [e]
                          (evt/stop-propagation! e)
                          (comp/transact! this
                            [(update-todo-done {:todo/id    id
                                                :todo/done? (not done?)})]
                            {:refresh [:all-todos]}))}))
          (if-not editing?
            (dd/list-item-text {:primary task})
            (layout/grid
              {:component  :form
               :onClick    evt/stop-propagation!
               :onFocus    evt/stop-propagation!
               :onSubmit   (fn submit-changed [e]
                             (evt/prevent-default! e)
                             (evt/stop-propagation! e)
                             (comp/transact! this [(edit-todo {:todo/id   id
                                                               :todo/task task})])
                             (m/set-value! this :ui/editing? false))
               :container  true
               :spacing    1
               :alignItems :flex-end}
              (layout/grid {:item true :style {:flexGrow 1}}
                (inputs/textfield
                  {:value     task
                   :fullWidth true
                   :onChange  (fn [e] (m/set-string! this :todo/task :event e))}))
              (layout/grid {:item true}
                (inputs/button
                  {:color :primary
                   :type  :submit
                   :size  :small}
                  "Save"))))))
      (dd/divider {:variant :middle})
      (surfaces/expansion-panel-details {}
        (dom/ul {:classes [chip-array]}
          (for [{:keys [label project] :as tag} tags]
            (dd/chip {:component :li
                      :key       (hash tag)
                      :label     label
                      :className (str/join " " [chip (str chip "--project")])}))))
      (dd/divider {:variant :middle})
      (surfaces/expansion-panel-actions {}
        (mui-input/button
          {:size    :small
           :onClick #(m/set-value! this :ui/editing? true)}
          "Edit")
        (mui-input/button
          {:size    :small
           :onClick #(comp/transact! this [(delete-todo {:todo/id id})])}
          "Delete")))))

(def ui-todo (comp/factory Todo {:keyfn :todo/id}))

(defmutation add-todo [{:keys [todo]}]
  (action [{:keys [state]}]
    (swap! state mrg/merge-component Todo todo :prepend [:all-todos]))
  (remote [env]
    (-> env
      (m/with-server-side-mutation 'decide.api.todo/add-todo)
      (m/with-target (targeting/prepend-to [:all-todos]))
      (m/returning Todo))))
