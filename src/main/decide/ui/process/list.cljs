(ns decide.ui.process.list
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [com.fulcrologic.fulcro.react.hooks :as hooks]
            [decide.models.process :as process]
            [decide.ui.process.new-process-form :as new-process-form]
            [material-ui.data-display :as dd]
            [material-ui.data-display.list :as list]
            [material-ui.lab :refer [skeleton]]
            [material-ui.layout :as layout]
            [material-ui.layout.grid :as grid]
            ["@material-ui/icons/Group" :default GroupIcon]
            ["@material-ui/icons/EmojiObjectsOutlined" :default EmojiObjectsOutlinedIcon]
            [material-ui.feedback :as feedback]
            [material-ui.inputs :as inputs]
            [com.fulcrologic.fulcro.mutations :as m]
            [taoensso.timbre :as log]))

(def page-ident [:PAGE :processes-list-page])

(defn icon-badge [title value icon-class]
  (grid/item {}
    (layout/box {:display :flex
                 :alignItems :center
                 :title title
                 :aria-label title
                 :color "text.secondary"}
      (dd/typography {:color :inherit} value)
      (layout/box {:m 1 :color :inherit :component icon-class}))))

(defsc ProcessListEntry [_ {::process/keys [slug title description no-of-participants no-of-proposals]}]
  {:query [::process/slug ::process/title ::process/description ::process/no-of-participants
           ::process/no-of-proposals]
   :ident ::process/slug}
  (list/item
    {:button true
     :component :a
     :href (str "/decision/" slug "/home")
     :divider true}
    (grid/container {:justify :space-between :spacing 1}
      (grid/item {:xs 12 :sm "auto"}
        (list/item-text {:primary title :secondary description}))
      (grid/container {:item true
                       :xs 12
                       :sm "auto"
                       :spacing 2
                       :alignItems :center
                       :style {:width "auto"}}
        (icon-badge "Anzahl Vorschläge" no-of-proposals EmojiObjectsOutlinedIcon)
        (icon-badge "Anzahl Teilnehmer" no-of-participants GroupIcon)))))


(def ui-process-list-entry (comp/computed-factory ProcessListEntry {:keyfn ::process/slug}))

(def skeleton-list
  (grid/container {:spacing 2}
    (grid/item {:xs 12} (skeleton {:variant "rect" :height "24px"}))
    (grid/item {:xs 12} (skeleton {:variant "rect" :height "24px"}))
    (grid/item {:xs 12} (skeleton {:variant "rect" :height "24px"}))))

(defsc AllProcessesList [this {:keys [all-processes] :as props}]
  {:query [{[:all-processes '_] (comp/get-query ProcessListEntry)}
           [df/marker-table :all-processes]]
   :initial-state (fn [_] {})
   :use-hooks? true}
  (hooks/use-lifecycle
    #(df/load! this :all-processes ProcessListEntry {:marker :all-processes}))
  (let [loading? (#{:loading} (get-in props [[df/marker-table :all-processes] :status]))]
    (comp/fragment
      (dd/typography {:component :h1 :variant :h2} "Aktive Entscheidungen")
      (list/list {}
        (map ui-process-list-entry all-processes))
      (when loading?
        (layout/box {}
          "Loading..."
          (when (empty? all-processes) skeleton-list))))))
(def ui-all-process-list (comp/factory AllProcessesList))


(defsc ProcessesPage [this {:keys [all-processes-list root/current-session
                                   ui/new-process-dialog-open? new-process-form]}]
  {:query [{:all-processes-list (comp/get-query AllProcessesList)}
           :ui/new-process-dialog-open?
           {:new-process-form (comp/get-query new-process-form/NewProcessForm)}
           [:root/current-session '_]]
   :ident (fn [] page-ident)
   :initial-state (fn [_] {:all-processes-list (comp/get-initial-state AllProcessesList)
                           :ui/new-process-dialog-open? false
                           :new-process-form (comp/get-initial-state new-process-form/NewProcessForm)})
   :route-segment ["decisions"]}
  (let [logged-in? (get current-session :session/valid? false)]
    (layout/container {}
      (ui-all-process-list all-processes-list)

      (inputs/button {:variant :text
                      :color :secondary
                      :disabled (not logged-in?)
                      :onClick #(m/toggle! this :ui/new-process-dialog-open?)} "Neuen Entscheidungsprozess anlegen")

      (feedback/dialog
        {:open new-process-dialog-open?
         :onClose #(m/set-value! this :ui/new-process-dialog-open? false)}
        (feedback/dialog-title {} "Neuer Entscheidungsprozess")
        (feedback/dialog-content {}
          (new-process-form/ui-new-process-form new-process-form
            {:onSubmit (fn [{::process/keys [title slug description]}]
                         (comp/transact! this [(process/add-process
                                                 {::process/title title
                                                  ::process/slug slug
                                                  ::process/description description})]))}))))))