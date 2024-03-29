(ns decide.ui.process.list
  (:require
   [com.fulcrologic.fulcro-i18n.i18n :as i18n]
   [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
   [com.fulcrologic.fulcro.data-fetch :as df]
   [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
   [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
   [decide.models.process :as process]
   [decide.models.process.mutations :as process.mutations]
   [decide.routes :as routes]
   [decide.ui.process.process-forms :as process-forms]
   [mui.data-display :as dd]
   [mui.feedback.dialog :as dialog]
   [mui.feedback.skeleton :refer [skeleton]]
   [mui.inputs :as inputs]
   [mui.layout :as layout]
   [mui.layout.grid :as grid]
   ["@mui/icons-material/EmojiObjectsOutlined" :default EmojiObjectsOutlinedIcon]
   ["@mui/icons-material/Group" :default GroupIcon]
   [mui.surfaces.card :as card]))

(def page-ident [:PAGE :processes-list-page])

(defn icon-badge [{:keys [title value icon]}]
  (dd/tooltip {:title title}
    (layout/box {:display :flex
                 :alignItems :center
                 :aria-label title
                 :mx 1
                 :color "text.secondary"}
      (dd/typography {:color :inherit} value)
      (layout/box {:m 1 :color :inherit :component icon}))))

(defsc ProcessListEntry [_ {::process/keys [slug title description no-of-participants no-of-proposals]}]
  {:query [::process/slug ::process/title ::process/description
           ::process/no-of-participants
           ::process/no-of-proposals]
   :ident ::process/slug
   :use-hooks? true}
  (grid/item {:xs 12}
    (card/card {}
      (card/action-area {:href (routes/href ::routes/process-home {:process/slug slug})}
        (card/header {:title title})
        (card/content {} description))

      (dd/divider {})
      (card/actions {}
        (let [no-of-proposals    (max no-of-proposals 0)
              no-of-participants (max no-of-participants 0)]
          (comp/fragment
            (icon-badge
              {:title
               (i18n/trf "{noOfProposals, plural, =1 {# proposal} other {# proposals}}" {:noOfProposals no-of-proposals})
               :value no-of-proposals
               :icon EmojiObjectsOutlinedIcon})

            (icon-badge
              {:title
               (i18n/trf "{noOfParticipants, plural, =1 {# participant} other {# participants}}" {:noOfParticipants no-of-participants})
               :value no-of-participants
               :icon GroupIcon})))))))


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
   :initial-state {}}
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
     :onClose close}
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
                     (close))}))))

(defsc ProcessesPage [this {:keys [all-processes-list
                                   ui/new-process-dialog-open?

                                   new-process-form]}]
  {:query [{:all-processes-list (comp/get-query AllProcessesList)}
           :ui/new-process-dialog-open?
           {:new-process-form (comp/get-query process-forms/NewProcessForm)}]
   :ident (fn [] page-ident)
   :initial-state {:ui/new-process-dialog-open? false
                   :new-process-form {}
                   :all-processes-list {}}
   :route-segment (routes/segment ::routes/process-list)
   :will-enter (fn [app]
                 (let [target (comp/get-ident ProcessesPage nil)]
                   (dr/route-deferred target
                     (fn []
                       (df/load! app :root/all-processes ProcessListEntry {:marker :all-processes})
                       (dr/target-ready! app target)))))}
  (let [logged-in?         (comp/shared this :logged-in?)
        new-process-button (inputs/button {:variant :tonal
                                           :disabled (not logged-in?)
                                           :fullWidth true
                                           :size :large
                                           :sx {:my 2}
                                           :onClick #(m/toggle! this :ui/new-process-dialog-open?)}
                             (i18n/tr "Create new decision-process"))]
    (layout/container {}
      (dd/typography {:component :h1 :variant :h2} (i18n/tr "Active decision-processes"))

      new-process-button

      (ui-all-process-list all-processes-list)

      new-process-button

      (new-process-dialog this {:close #(m/set-value! this :ui/new-process-dialog-open? false)
                                :new-process-dialog-open? new-process-dialog-open?
                                :new-process-form new-process-form}))))