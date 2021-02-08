(ns decide.ui.process.list
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [com.fulcrologic.fulcro.react.hooks :as hooks]
            [decide.models.process :as process]
            [decide.models.user :as user]
            [decide.ui.process.process-forms :as process-forms]
            [material-ui.data-display :as dd]
            [material-ui.lab :refer [skeleton]]
            [material-ui.layout :as layout]
            [material-ui.layout.grid :as grid]
            ["@material-ui/icons/Group" :default GroupIcon]
            ["@material-ui/icons/EmojiObjectsOutlined" :default EmojiObjectsOutlinedIcon]
            ["@material-ui/icons/Edit" :default EditIcon]
            [material-ui.feedback.dialog :as dialog]
            [material-ui.inputs :as inputs]
            [taoensso.timbre :as log]
            [material-ui.surfaces :as surfaces]
            [com.fulcrologic.fulcro.algorithms.merge :as mrg]))

(def page-ident [:PAGE :processes-list-page])

(defsc EditProcessDialog [this {:keys [ui/open? process-form]}]
  {:query [:ui/open?
           {:process-form (comp/get-query process-forms/EditProcessForm)}]
   :ident (fn [] [:component/id ::EditProcessDialog])
   :initial-state
   (fn [_]
     {:ui/open? false
      :process-form nil})}
  (let [slug (::process/slug (:process process-form))]
    (dialog/dialog
      {:open (and slug open?)
       :onClose #(m/set-value! this :ui/open? false)}
      (dialog/title {} "Entscheidungsprozess bearbeiten")
      (dialog/content {}
        (process-forms/ui-edit-process-form process-form
          {:onSubmit
           (fn [{::process/keys [title description end-time]}]
             (comp/transact! this [(process/update-process
                                     {::process/title title
                                      ::process/slug slug
                                      ::process/description description
                                      ::process/end-time end-time})
                                   (m/set-props {:ui/open? false})]))})))))

(def ui-edit-process-dialog (comp/computed-factory EditProcessDialog))

(defmutation show-edit-process-dialog [{:keys [process]}]
  (action [{:keys [app]}]
    (if-not (::process/slug process)
      (log/error "The" :process "passed to" `show-edit-process-dialog "must have the key" ::process/slug "!")
      (mrg/merge-component! app EditProcessDialog
        {:ui/open? true
         :process-form (comp/get-initial-state process-forms/EditProcessForm {:process process})}))))

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
    (contains? (into #{} (map (partial comp/get-ident Moderator)) moderators)
      (:user current-session))))

(defsc ProcessListEntry [this {::process/keys [slug title description no-of-authors no-of-proposals moderators]
                               :keys [root/current-session]}]
  {:query [::process/slug ::process/title ::process/description ::process/no-of-authors
           ::process/no-of-proposals
           {::process/moderators (comp/get-query Moderator)}
           [:root/current-session '_]]
   :ident ::process/slug
   :use-hooks? true}
  (let [moderator? (is-moderator? moderators current-session)
        [edit-title? set-edit-title?] (hooks/use-state false)
        submit-title (hooks/use-callback
                       (fn []
                         (set-edit-title? false)
                         (comp/transact! this [(process/update-process {::process/slug slug ::process/title title})]))
                       [title])]
    (grid/item {:xs 12}
      (surfaces/card {}
        (if-not moderator?
          (surfaces/card-action-area {:href (str "/decision/" slug "/home")}
            (surfaces/card-header {:title title})
            (surfaces/card-content {} description))

          (comp/fragment
            (surfaces/card-header
              {:title
               (if edit-title?
                 (inputs/textfield
                   {:value title
                    :fullWidth true
                    :onChange #(m/set-string!! this ::process/title :event %)
                    :onBlur submit-title
                    :InputProps {:endAdornment (inputs/button {:onClick submit-title} "Save")}})
                 (layout/box {}
                   title
                   (inputs/icon-button {:onClick #(set-edit-title? true)
                                        :aria-label "Edit title"
                                        :title "Edit title"
                                        :size :small}
                     (layout/box {:component EditIcon :fontSize :small}))))})
            (surfaces/card-action-area {:href (str "/decision/" slug "/home")}
              (surfaces/card-content {} description))))

        (dd/divider {})
        (surfaces/card-actions {}
          (icon-badge "Anzahl Vorschläge" (or no-of-proposals 0) EmojiObjectsOutlinedIcon)
          (icon-badge "Anzahl Teilnehmer" (or no-of-authors 0) GroupIcon)
          (when moderator?
            (inputs/button {:color "inherit" :startIcon (layout/box {:component EditIcon})
                            :onClick #(comp/transact! this [(show-edit-process-dialog {:process {::process/slug slug}})])}
              "Bearbeiten")))))))


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
      (grid/container {:spacing 1}
        (map ui-process-list-entry all-processes))
      (when loading?
        (layout/box {}
          "Loading..."
          (when (empty? all-processes) skeleton-list))))))
(def ui-all-process-list (comp/factory AllProcessesList))

(defn new-process-dialog [comp {:keys [new-process-dialog-open? new-process-form]}]
  (dialog/dialog
    {:open new-process-dialog-open?
     :onClose #(m/set-value! comp :ui/new-process-dialog-open? false)}
    (dialog/title {} "Neuer Entscheidungsprozess")
    (dialog/content {}
      (process-forms/ui-new-process-form new-process-form
        {:onSubmit (fn [{::process/keys [title slug description end-time]}]
                     (comp/transact! comp [(process/add-process
                                             {::process/title title
                                              ::process/slug slug
                                              ::process/description description
                                              ::process/end-time end-time})]))}))))

(defsc ProcessesPage [this {:keys [all-processes-list root/current-session
                                   ui/new-process-dialog-open?

                                   new-process-form
                                   ui/edit-process-dialog]}]
  {:query [{:all-processes-list (comp/get-query AllProcessesList)}
           :ui/new-process-dialog-open?
           {:new-process-form (comp/get-query process-forms/NewProcessForm)}
           {:ui/edit-process-dialog (comp/get-query EditProcessDialog)}
           [:root/current-session '_]]
   :ident (fn [] page-ident)
   :initial-state (fn [_] {:all-processes-list (comp/get-initial-state AllProcessesList)
                           :ui/new-process-dialog-open? false
                           :new-process-form (comp/get-initial-state process-forms/NewProcessForm)
                           :ui/edit-process-dialog (comp/get-initial-state EditProcessDialog)})
   :route-segment ["decisions"]}
  (let [logged-in? (get current-session :session/valid? false)]
    (layout/container {}
      (dd/typography {:component :h1 :variant :h2} "Aktive Entscheidungen")
      (ui-all-process-list all-processes-list)

      (layout/box {:my 2 :clone true}
        (inputs/button {:variant :text
                        :color :primary
                        :disabled (not logged-in?)
                        :fullWidth true
                        :size :large
                        :onClick #(m/toggle! this :ui/new-process-dialog-open?)}
          "Neuen Entscheidungsprozess anlegen"))

      (ui-edit-process-dialog edit-process-dialog)
      (new-process-dialog this {:new-process-dialog-open? new-process-dialog-open?
                                :new-process-form new-process-form}))))