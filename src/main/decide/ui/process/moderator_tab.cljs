(ns decide.ui.process.moderator-tab
  (:require
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [decide.models.process :as process]
    [decide.models.user :as user]
    [decide.ui.common.time :as time]
    [material-ui.data-display :as dd]
    [material-ui.data-display.list :as list]
    [material-ui.inputs :as inputs]
    [material-ui.inputs.form :as form]
    [material-ui.layout :as layout]
    [material-ui.layout.grid :as grid]
    [material-ui.surfaces :as surfaces]
    ["@material-ui/icons/ExpandMore" :default ExpandMoreIcon]
    ["@material-ui/icons/RemoveCircleOutline" :default RemoveCircleIcon]))

(defn- accordion [{:keys [title]} body]
  (surfaces/accordion {}
    (surfaces/accordion-panel-summary {:expandIcon (layout/box {:component ExpandMoreIcon})}
      (dd/typography {:variant "body1"} title))
    (surfaces/accordion-panel-details {} body)))

(defsc Moderator [_ {::user/keys [id display-name]
                     :keys [root/current-session]} {:keys [onDelete]}]
  {:query [::user/id ::user/display-name
           {[:root/current-session '_] 1}]; TODO Replace join with Session.
   :ident ::user/id}
  (let [self? (= id (::user/id current-session))]
    (list/item {}
      (list/item-avatar {}
        (dd/avatar {} (first display-name)))
      (list/item-text {:primary display-name})
      (when onDelete
        (list/item-secondary-action {}
          (inputs/icon-button
            {:edge :end
             :disabled self? ; can't remove yourself from moderators
             :onClick onDelete}
            (dom/create-element RemoveCircleIcon)))))))

(def ui-moderator (comp/computed-factory Moderator {:keyfn ::user/id}))


(defsc ModeratorList [this {::process/keys [slug moderators]}]
  {:query [::process/slug {::process/moderators (comp/get-query Moderator)}]
   :ident ::process/slug
   :use-hooks? true}
  (let [[new-moderator-email set-new-moderator-email] (hooks/use-state "")]
    (accordion {:title "Moderatoren"}
      (grid/container {:spacing 2}
        (grid/item {:xs 12}
          (list/list {}
            (->> moderators
              (sort-by ::user/display-name)
              (map ui-moderator)
              vec)))
        (grid/item
          {:component :form :xs 12
           :onSubmit
           (fn [e]
             (evt/prevent-default! e)
             (set-new-moderator-email "")
             (comp/transact! this [(process/add-moderator {::process/slug slug :email new-moderator-email})]))}
          (dd/typography {:variant :h6} "Moderator hinzufügen")
          (inputs/textfield
            {:label "E-Mail"
             :value new-moderator-email
             :onChange #(set-new-moderator-email (evt/target-value %))
             :fullWidth true
             :InputProps {:endAdornment (inputs/button {:type :submit} "Hinzufügen")}}))))))

(def ui-moderator-list (comp/computed-factory ModeratorList))

(def default-input-props
  {:fullWidth true
   :variant "filled"
   :autoComplete "off"
   :margin "normal"})

(defsc ProcessEdit [this {::process/keys [slug title description end-time]}]
  {:query [::process/slug ::process/title ::process/description ::process/end-time]
   :ident ::process/slug
   :use-hooks? true}
  (accordion {:title "Prozess bearbeiten"}
    (let [[mod-title change-title] (hooks/use-state nil)
          [mod-description change-description] (hooks/use-state description)
          [with-end? set-with-end?] (hooks/use-state (boolean end-time))
          [mod-end-time set-end-time] (hooks/use-state end-time)]
      (grid/container
        {:component :form
         :onSubmit
         (fn [evt]
           (evt/prevent-default! evt)
           (comp/transact! this [(process/update-process
                                   {::process/slug slug
                                    ::process/title mod-title
                                    ::process/description mod-description
                                    ::process/end-time (and mod-end-time (js/Date. mod-end-time))})]))}
        (inputs/textfield
          (merge default-input-props
            {:label "Titel"
             :value (or mod-title title)
             :helperText (when (not= title mod-title) "Bearbeitet")
             :onChange #(change-title (evt/target-value %))
             :inputProps {:maxLength 140}}))

        (inputs/textfield
          (merge default-input-props
            {:label "Beschreibung"
             :helperText (when (not= description mod-description) "Bearbeitet")
             :multiline true
             :rows 7
             :value mod-description
             :onChange #(change-description (evt/target-value %))}))

        (form/group {:row true}
          (form/control-label
            {:label "Hat der Prozess ein Ende?"
             :control
             (inputs/switch
               {:checked with-end?
                :onChange #(set-with-end? (not with-end?))})}))

        (time/datetime-picker
          {:label "Ende"
           :value mod-end-time
           :disabled (not with-end?)
           :inputVariant "filled"
           :onChange set-end-time
           :fullWidth true})

        (grid/item {:xs 12}
          (inputs/button {:color :primary :type "submit"} "Speichern"))))))

(def ui-process-edit (comp/computed-factory ProcessEdit))

(defsc Process [_ _]
  {:query (fn []
            (->> [[::process/slug]
                  (comp/get-query ProcessEdit)
                  (comp/get-query ModeratorList)]
              (apply concat) set vec))
   :ident ::process/slug})

(defmutation init-moderator-tab [{:keys [slug]}]
  (action [{:keys [app ref]}]
    (let [process-ident [::process/slug slug]]

      ; (df/load! app process-ident ModeratorList {:target (conj ref :moderator-list)})
      ; (df/load! app process-ident ProcessEdit {:target (conj ref :process-edit)})
      ;; combine loads of same entity into one.
      (df/load! app process-ident Process
        {:target (targeting/multiple-targets
                   (conj ref :moderator-list)
                   (conj ref :process)
                   (conj ref :process-edit))
         :post-mutation `dr/target-ready
         :post-mutation-params {:target ref}}))))

(defsc ProcessModeratorTab [this {:keys [moderator-list process-edit process]}]
  {:query [{:process (comp/get-query Process)}
           {:process-edit (comp/get-query ProcessEdit)}
           {:moderator-list (comp/get-query ModeratorList)}]
   :ident (fn [] [::ProcessModeratorTab (::process/slug process)])
   :route-segment ["moderate"]
   :will-enter
   (fn [app {::process/keys [slug]}]
     (let [ident (comp/get-ident ProcessModeratorTab {:process {::process/slug slug}})]
       (dr/route-deferred ident
         #(comp/transact! app [(init-moderator-tab {:slug slug})] {:ref ident}))))}
  (layout/container {}
    (layout/box {:my 2}
      (ui-process-edit process-edit)
      (ui-moderator-list moderator-list))))