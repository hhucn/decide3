(ns decide.appbar-ws
  (:require [nubank.workspaces.core :as ws]
            [nubank.workspaces.model :as wsm]
            [nubank.workspaces.card-types.fulcro3 :as ct.fulcro]
            [decide.ui.components.appbar :as appbar]))

(ws/defcard appbar-card
  {::wsm/card-width  7
   ::wsm/card-height 5
   ::wsm/align       {:flex 1}}
  (ct.fulcro/fulcro-card
    {::ct.fulcro/root appbar/AppBar
     ::ct.fulcro/wrap-root? false}))