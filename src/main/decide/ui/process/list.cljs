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
            [material-ui.feedback :as feedback]
            [material-ui.inputs :as inputs]
            [com.fulcrologic.fulcro.mutations :as m]
            [taoensso.timbre :as log]))

(def page-ident [:PAGE :processes-list-page])

(defsc ProcessListEntry [_ {::process/keys [slug title no-of-participants]}]
  {:query [::process/slug ::process/title ::process/no-of-participants]
   :ident ::process/slug}
  (list/item
    {:button true
     :component :a
     :href (str "/decision/" slug "/home")
     :divider true}
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
   :initial-state (fn [_] {})
   :use-hooks? true}
  (hooks/use-lifecycle
    #(df/load! this :all-processes ProcessListEntry {:marker :all-processes}))
  (let [loading? (#{:loading} (get-in props [[df/marker-table :all-processes] :status]))]
    (comp/fragment
      (dd/typography {:component :h1 :variant :h2} "Aktive Entscheidungsprozesse")
      (list/list {}
        (map ui-process-list-entry all-processes))
      (when loading?
        (layout/box {}
          "Loading..."
          (when (empty? all-processes) skeleton-list))))))
(def ui-all-process-list (comp/factory AllProcessesList))

(defn new-process-dialog [props & children]
  (feedback/dialog props
    children))


(defsc ProcessesPage [this {:keys [all-processes-list root/current-session
                                   ui/new-process-dialog-open? new-process-form]}]
  {:query [{:all-processes-list (comp/get-query AllProcessesList)}
           :ui/new-process-dialog-open?
           {:new-process-form (comp/get-query new-process-form/NewProcessForm)}
           [:root/current-session '_]]
   :ident (fn [] page-ident)
   :initial-state (fn [_] {:all-processes-list (comp/get-initial-state AllProcessesList)
                           :new-process-dialog-open? false
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