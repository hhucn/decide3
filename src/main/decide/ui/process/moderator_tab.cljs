(ns decide.ui.process.moderator-tab
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
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
    [decide.utils.time :as time]
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
    (accordion {:title (i18n/tr "Moderators")}
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
             (comp/transact! this [(process/add-moderator {::process/slug slug ::user/email new-moderator-email})]))}
          (dd/typography {:variant :h6} (i18n/tr "Add moderator"))
          (inputs/textfield
            {:label (i18n/tr "Email")
             :value new-moderator-email
             :onChange #(set-new-moderator-email (evt/target-value %))
             :fullWidth true
             :InputProps {:endAdornment (inputs/button {:type :submit} (i18n/trc "Submit new moderator form" "Add"))}}))))))

(def ui-moderator-list (comp/computed-factory ModeratorList))

(def default-input-props
  {:fullWidth true
   :variant "filled"
   :autoComplete "off"
   :margin "normal"})

(defsc ProcessEdit [this {::process/keys [slug title description end-time type]}]
  {:query [::process/slug ::process/title ::process/description ::process/end-time ::process/type]
   :ident ::process/slug
   :use-hooks? true}
  (accordion {:title (i18n/tr "Edit process")}
    (let [[mod-title change-title] (hooks/use-state title)
          [mod-description change-description] (hooks/use-state description)
          [with-end? set-with-end?] (hooks/use-state (boolean end-time))
          [mod-end-time set-end-time] (hooks/use-state end-time)
          [mod-type set-type] (hooks/use-state type)]
      (grid/container
        {:component :form
         :onSubmit
         (fn [evt]
           (evt/prevent-default! evt)

           (comp/transact! this [(process/update-process
                                   ;; calculate diff ;; NOTE have a look at clojure.data/diff
                                   (cond-> {::process/slug slug}
                                     (not= mod-title title)
                                     (assoc ::process/title mod-title)

                                     (not= mod-description description)
                                     (assoc ::process/description mod-description)

                                     (not= mod-type type)
                                     (assoc ::process/type mod-type)

                                     (not= (when with-end? mod-end-time) end-time)
                                     (assoc ::process/end-time (when with-end? mod-end-time))))]))}
        (inputs/textfield
          (merge default-input-props
            {:label (i18n/trc "Title of a process" "Title")
             :value mod-title
             :helperText (when (not= title mod-title) (i18n/tr "Edit"))
             :onChange #(change-title (evt/target-value %))
             :inputProps {:maxLength 140}}))

        (inputs/textfield
          (merge default-input-props
            {:label (i18n/trc "Description of a process" "Description")
             :helperText (when (not= description mod-description) (i18n/tr "Edit"))
             :multiline true
             :rows 7
             :value mod-description
             :onChange #(change-description (evt/target-value %))}))

        (form/group {:row true}
          (form/control-label
            {:label (i18n/tr "Is the process public?")
             :control
             (inputs/switch
               {:checked (= mod-type ::process/type.public)
                :onChange #(set-type (if (= mod-type ::process/type.public) ::process/type.private ::process/type.public))})}))

        (form/group {:row true}
          (form/control-label
            {:label (i18n/tr "Does the process end?")
             :control
             (inputs/switch
               {:checked with-end?
                :onChange #(set-with-end? (not with-end?))})}))

        (time/datetime-picker
          {:label (i18n/trc "End of a process" "End")
           :value mod-end-time
           :disabled (not with-end?)
           :inputVariant "filled"
           :onChange set-end-time
           :fullWidth true})

        (grid/item {:xs 12}
          (inputs/button {:color :primary :type "submit"} (i18n/trc "Submit form" "Submit")))))))

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