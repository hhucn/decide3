(ns decide.process-ws
  (:require
   [com.fulcrologic.fulcro.algorithms.react-interop :as interop]
   [com.fulcrologic.fulcro.components :as comp]
   [com.fulcrologic.fulcro.dom :as dom]
   ["@mui/icons-material/EmojiObjectsOutlined" :default EmojiObjectsOutlinedIcon]
   [decide.models.process :as process]
   [decide.ui.process.list :as list]
   [decide.ui.proposal.card :as card]
   [decide.ui.theming.themes :as themes]
   [mui.styles :as styles]
   [nubank.workspaces.card-types.fulcro3 :as ct.fulcro]
   [nubank.workspaces.card-types.react :as ct.react]
   [nubank.workspaces.core :as ws]
   [nubank.workspaces.model :as wsm]
   ["@mui/material/ScopedCssBaseline" :default ScopedCssBaseline]
   [taoensso.timbre :as log]))


(def scoped-css-baseline (interop/react-factory ScopedCssBaseline))

(ws/defcard process-list-item-card
  {::wsm/card-width 7
   ::wsm/card-height 5
   ::wsm/align {:flex 1}}
  (ct.fulcro/fulcro-card
    {::ct.fulcro/root list/ProcessListEntry
     ::ct.fulcro/initial-state
     (fn [] {::process/slug "test"
             ::process/title "This is a test"
             ::process/description "bla"
             ::process/no-of-authors 42
             ::process/no-of-proposals 60})}))

(defn render-at [c node]
  (let [comp (if (fn? c) (c) c)]
    (js/ReactDOM.render comp node)))

(defn fulcro-theme-card-init [card config]
  (let [{::wsm/keys [render] :as react-card
         :keys [::ct.fulcro/app]} (log/spy :info (ct.fulcro/fulcro-card-init card config))]
    (update react-card
      ::wsm/render
      (fn [render]
        (fn [node]
          (render node)))
      #_(fn [node]
          (let [theme-provider
                (scoped-css-baseline {}
                  (styles/theme-provider
                    {:theme (themes/get-mui-theme :dark)}
                    (dom/div {} "Container")))]
            (js/ReactDOM.render theme-provider node
              #(render (.-firstChild (.-firstChild node)))))))))

(defn fulcro-theme-card [config]
  {::wsm/init
   #(fulcro-theme-card-init % config)})

(ws/defcard proposal-VotingArea-card
  (fulcro-theme-card
    {::ct.fulcro/root card/VotingArea
     ::ct.fulcro/initial-state
     (fn [] (comp/computed
              #:decide.models.proposal{:id #uuid"550e8400-e29b-11d4-a716-446655440000"
                                       :pro-votes 42
                                       :my-opinion-value 1}
              {:process {}}))}))

(ws/defcard process-all-processes-list
  (fulcro-theme-card
    {::ct.fulcro/root list/AllProcessesList
     ::ct.fulcro/root-state
     {:root/all-processes [[::process/slug "test"] [::process/slug "test2"]]
      ::process/slug {"test"
                      {::process/slug "test"
                       ::process/title "This is a test"
                       ::process/description "bla"
                       ::process/no-of-authors 42
                       ::process/no-of-proposals 60}
                      "test2"
                      {::process/slug "test2"
                       ::process/title "This is also test"
                       ::process/description "bla"
                       ::process/no-of-authors 4
                       ::process/no-of-proposals 6}}}}))

(ws/defcard process-icon-badge
  (ct.react/react-card (list/icon-badge {:title "Anzahl Vorschläge", :value 42, :icon EmojiObjectsOutlinedIcon})))