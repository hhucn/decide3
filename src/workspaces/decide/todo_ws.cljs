(ns decide.todo-ws
  (:require [nubank.workspaces.core :as ws]
            [nubank.workspaces.model :as wsm]
            [nubank.workspaces.card-types.fulcro3 :as ct.fulcro]
            [decide.models.todo :as todo]
            [decide.ui.components.new-todo-field :as new-todo-field]
            [decide.ui.root :as r]))

(ws/defcard todo-card
  {::wsm/align {:flex 1}}
  (ct.fulcro/fulcro-card
    {::ct.fulcro/root          todo/Todo
     ::ct.fulcro/initial-state (fn [] #:todo{:id    (random-uuid)
                                             :task  "Add more cards"
                                             :done? false
                                             :tags  #{{:label "Project1"
                                                       :type  :project}
                                                      {:label "Project2"
                                                       :type  :project}}})}))

(ws/defcard root-card
  (ct.fulcro/fulcro-card
    {::ct.fulcro/root r/Root
     ::ct.fulcro/wrap-root? false}))