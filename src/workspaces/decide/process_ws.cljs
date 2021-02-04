(ns decide.process-ws
  (:require
    [decide.models.process :as process]
    [decide.ui.process.list :as list]
    [decide.ui.theming.themes :as themes]
    ["@material-ui/icons/EmojiObjectsOutlined" :default EmojiObjectsOutlinedIcon]
    [material-ui.styles :as styles]
    [nubank.workspaces.card-types.fulcro3 :as ct.fulcro]
    [nubank.workspaces.card-types.react :as ct.react]
    [nubank.workspaces.card-types.util :as ct.util]
    [nubank.workspaces.core :as ws]
    [nubank.workspaces.model :as wsm]
    [com.fulcrologic.fulcro.dom :as dom]
    [taoensso.timbre :as log]
    [com.fulcrologic.fulcro.react.hooks :as hooks]))


(ws/defcard process-list-item-card
  {::wsm/card-width  7
   ::wsm/card-height 5
   ::wsm/align       {:flex 1}}
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
  (let [{::wsm/keys [refresh render] :as react-card
         :keys [::ct.fulcro/app]} (ct.fulcro/fulcro-card-init card config)]
    (assoc react-card
      ::wsm/refresh
      (fn [node]
        (refresh node))

      ::wsm/render
      (fn [node]
        (let [theme-provider
              (js/React.forwardRef
                (fn [props ref]
                  (let [props (log/spy :info (js->clj props))]
                    (log/spy :info ref)
                    (styles/theme-provider
                      {:theme themes/light-theme}
                      (dom/div {:ref ref}
                        (.-children props))))))
              ref (log/spy :info (js/React.createRef))
              theme-element (js/React.createElement theme-provider {:ref ref})]
          (log/spy :info theme-element)
          (js/ReactDOM.render theme-element node)
          (log/spy :info ref)
          (render (.-current ref)))))))

(defn fulcro-theme-card [config]
  {::wsm/init
   #(fulcro-theme-card-init % config)})

(ws/defcard process-all-processes-list
  (ct.fulcro/fulcro-card
    {::ct.fulcro/root list/AllProcessesList
     ::ct.fulcro/root-state
     {:all-processes [[::process/slug "test"] [::process/slug "test2"]]
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
  (ct.react/react-card (list/icon-badge "Anzahl Vorschläge" 42 EmojiObjectsOutlinedIcon)))