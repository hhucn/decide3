(ns decide.ui.process.list
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [com.fulcrologic.fulcro.react.hooks :as hooks]
            [decide.models.process :as process]
            [decide.models.user :as user]
            [decide.ui.process.new-process-form :as new-process-form]
            [material-ui.data-display :as dd]
            [material-ui.data-display.list :as list]
            [material-ui.lab :refer [skeleton]]
            [material-ui.layout :as layout]
            [material-ui.layout.grid :as grid]
            ["@material-ui/icons/Group" :default GroupIcon]
            ["@material-ui/icons/EmojiObjectsOutlined" :default EmojiObjectsOutlinedIcon]
            ["@material-ui/icons/Edit" :default EditIcon]
            [material-ui.feedback :as feedback]
            [material-ui.inputs :as inputs]
            [com.fulcrologic.fulcro.mutations :as m]
            [taoensso.timbre :as log]
            [material-ui.surfaces :as surfaces]))

(def page-ident [:PAGE :processes-list-page])

(defn icon-badge [title value icon-class]
  (layout/box {:display :flex
               :alignItems :center
               :title title
               :aria-label title
               :mx 1
               :color "text.secondary"}
    (dd/typography {:color :inherit} value)
    (layout/box {:m 1 :color :inherit :component icon-class})))

(defsc Moderator [_ {::user/keys [id display-name]}]
  {:query [::user/id ::user/display-name]
   :ident ::user/id})

(defn- is-moderator? [moderators current-session]
  (and
    (:session/valid? current-session)
    (contains? (set (map (partial comp/get-ident Moderator) moderators))
      (:user current-session))))


(defsc ProcessListEntry [_ {::process/keys [slug title description no-of-authors no-of-proposals moderators]
                            :keys [root/current-session]}]
  {:query [::process/slug ::process/title ::process/description ::process/no-of-authors
           ::process/no-of-proposals
           {::process/moderators (comp/get-query Moderator)}
           [:root/current-session '_]]
   :ident ::process/slug}
  (grid/item {:xs 12}
    (surfaces/card {}
      (surfaces/card-action-area {:href (str "/decision/" slug "/home")}
        (surfaces/card-header {:title title})
        (surfaces/card-content {} description))
      (dd/divider {})
      (surfaces/card-actions {}
        (icon-badge "Anzahl Vorschläge" (or no-of-proposals 0) EmojiObjectsOutlinedIcon)
        (icon-badge "Anzahl Teilnehmer" (or no-of-authors 0) GroupIcon)
        (when (is-moderator? moderators current-session)
          (inputs/button {:color "inherit"}
            "Bearbeiten"))))))


(def ui-process-list-entry (comp/computed-factory ProcessListEntry {:keyfn ::process/slug}))

(def skeleton-list
  (let [entry (grid/item {:xs 12} (skeleton {:variant "rect" :height "150px"}))]
    (grid/container {:spacing 2}
      entry
      entry
      entry)))

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
      (grid/container {:spacing 1}
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

      (layout/box {:my 2 :clone true}
        (inputs/button {:variant :outlined
                        :color :secondary
                        :disabled (not logged-in?)
                        :fullWidth true
                        :size :large
                        :onClick #(m/toggle! this :ui/new-process-dialog-open?)}
          "Neuen Entscheidungsprozess anlegen"))

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