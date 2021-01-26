(ns decide.ui.process.list
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [com.fulcrologic.fulcro.react.hooks :as hooks]
            [decide.models.process :as process]
            [material-ui.data-display :as dd]
            [material-ui.data-display.list :as list]
            [material-ui.lab :refer [skeleton]]
            [material-ui.layout :as layout]
            [material-ui.layout.grid :as grid]
            ["@material-ui/icons/Group" :default GroupIcon]))

(def page-ident [:PAGE :processes-list-page])

(defsc ProcessListEntry [_ {::process/keys [slug title no-of-participants]}]
  {:query [::process/slug ::process/title ::process/no-of-participants]
   :ident ::process/slug}
  (list/item {:button true :component :a :href (str "/decision/" slug "/home")}
    (list/item-text {} title)
    (list/item-secondary-action {}
      (layout/box {:display :inline-flex :title "Anzahl Teilnehmer" :aria-label "Anzahl Teilnehmer"}
        (dd/typography {} no-of-participants)
        (layout/box {:ml 1 :component GroupIcon})))))


(def ui-process-list-entry (comp/computed-factory ProcessListEntry {:keyfn ::process/slug}))

(def skeleton-list
  (grid/container {:spacing 2}
    (grid/item {:xs 12} (skeleton {:variant "rect" :height "24px"}))
    (grid/item {:xs 12} (skeleton {:variant "rect" :height "24px"}))
    (grid/item {:xs 12} (skeleton {:variant "rect" :height "24px"}))))

(defsc AllProcessesList [this {:keys [all-processes] :as props}]
  {:query [{[:all-processes '_] (comp/get-query ProcessListEntry)}
           [df/marker-table :all-processes]]
   :initial-state {}
   :use-hooks? true}
  (hooks/use-lifecycle
    #(df/load! this :all-processes ProcessListEntry {:marker :all-processes}))
  (let [loading? (#{:loading} (get-in props [[df/marker-table :all-processes] :status]))]
    (comp/fragment
      (dd/typography {:component :h1 :variant :h2} "Alle Entscheidungen")
      (list/list {}
        (map ui-process-list-entry all-processes))
      (when loading?
        (comp/fragment "Loading..."
          (when (empty? all-processes) skeleton-list))))))
(def ui-all-process-list (comp/factory AllProcessesList))

(defsc ProcessesPage [this {:keys [all-processes-list]}]
  {:query [{:all-processes-list (comp/get-query AllProcessesList)}]
   :ident (fn [] page-ident)
   :initial-state (fn [_] {:all-processes-list (comp/get-initial-state AllProcessesList)})
   :route-segment ["decisions"]}
  (layout/container {}
    (ui-all-process-list all-processes-list)))