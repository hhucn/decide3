(ns decide.ui.process.list
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [decide.models.process :as process]
    [decide.models.process.mutations :as process.mutations]
    [decide.ui.process.process-forms :as process-forms]
    [mui.data-display :as dd]
    [mui.feedback.dialog :as dialog]
    [mui.inputs :as inputs]
    [mui.feedback.skeleton :refer [skeleton]]
    [mui.layout :as layout]
    [mui.layout.grid :as grid]
    [mui.surfaces :as surfaces]
    [mui.surfaces.card :as card]
    ["@mui/icons-material/EmojiObjectsOutlined" :default EmojiObjectsOutlinedIcon]
    ["@mui/icons-material/Group" :default GroupIcon]))

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

(defsc ProcessListEntry [_ {::process/keys [slug title description no-of-participants no-of-proposals]}]
  {:query [::process/slug ::process/title ::process/description
           ::process/no-of-participants
           ::process/no-of-proposals]
   :ident ::process/slug
   :use-hooks? true}
  (grid/item {:xs 12}
    (card/card {}
      (card/action-area {:href (str "/decision/" slug "/home")}
        (card/header {:title title})
        (card/content {} description))

      (dd/divider {})
      (card/actions {}
        (icon-badge (i18n/tr "Number of proposals") (or no-of-proposals 0) EmojiObjectsOutlinedIcon)
        (icon-badge (i18n/tr "Number of participants") (max no-of-participants 0) GroupIcon)))))


(def ui-process-list-entry (comp/computed-factory ProcessListEntry {:keyfn ::process/slug}))

(def skeleton-list
  (let [entry (grid/item {:xs 12} (skeleton {:variant "rect" :height "150px"}))]
    (grid/container {:spacing 2}
      entry
      entry
      entry)))

(defsc AllProcessesList [this {:root/keys [all-processes] :as props}]
  {:query [{[:root/all-processes '_] (comp/get-query ProcessListEntry)}
           [df/marker-table :all-processes]]
   :initial-state (fn [_] {})
   :use-hooks? true}
  (hooks/use-lifecycle ; TODO This feels bad... Better to load somewhere before this even gets rendered.
    #(df/load! this :root/all-processes ProcessListEntry {:marker :all-processes}))
  (let [loading? (df/loading? (get props [df/marker-table :all-processes]))]
    (comp/fragment
      (grid/container {:spacing 1}
        (map ui-process-list-entry all-processes))
      (when loading?
        (layout/box {}
          (i18n/trc "Load indicator" "Loading...")
          (when (empty? all-processes) skeleton-list))))))
(def ui-all-process-list (comp/factory AllProcessesList))

(defn new-process-dialog [comp {:keys [close new-process-dialog-open? new-process-form]}]
  (dialog/dialog
    {:open new-process-dialog-open?
     :onClose #(m/set-value! comp :ui/new-process-dialog-open? false)}
    (dialog/title {} (i18n/tr "New decision-process"))
    (dialog/content {}
      (process-forms/ui-new-process-form new-process-form
        {:onSubmit (fn [{::process/keys [title slug description end-time type] :keys [participant-emails]}]
                     (comp/transact! comp [(process.mutations/add-process
                                             {::process/title title
                                              ::process/slug slug
                                              ::process/description description
                                              ::process/end-time end-time
                                              ::process/type type
                                              :participant-emails participant-emails})])
                     #_(close))}))))

(defsc ProcessesPage [this {:keys [all-processes-list root/current-session
                                   ui/new-process-dialog-open?

                                   new-process-form]}]
  {:query [{:all-processes-list (comp/get-query AllProcessesList)}
           :ui/new-process-dialog-open?
           {:new-process-form (comp/get-query process-forms/NewProcessForm)}
           [:root/current-session '_]]
   :ident (fn [] page-ident)
   :initial-state (fn [_] {:all-processes-list (comp/get-initial-state AllProcessesList)
                           :ui/new-process-dialog-open? false
                           :new-process-form (comp/get-initial-state process-forms/NewProcessForm)})
   :route-segment ["decisions"]}
  (let [logged-in? (get current-session :session/valid? false)]
    (layout/container {}
      (dd/typography {:component :h1 :variant :h2} (i18n/tr "Active decision-processes"))

      (inputs/button {:variant :contained
                      :color :primary
                      :disabled (not logged-in?)
                      :fullWidth true
                      :size :large
                      :sx {:my 2}
                      :onClick #(m/toggle! this :ui/new-process-dialog-open?)}
        (i18n/tr "Create new decision-process"))

      (ui-all-process-list all-processes-list)

      (inputs/button {:variant :text
                      :color :primary
                      :disabled (not logged-in?)
                      :fullWidth true
                      :size :large
                      :sx {:my 2}
                      :onClick #(m/toggle! this :ui/new-process-dialog-open?)}
        (i18n/tr "Create new decision-process"))

      (new-process-dialog this {:close #(m/set-value! this :ui/new-process-dialog-open? false)
                                :new-process-dialog-open? new-process-dialog-open?
                                :new-process-form new-process-form}))))