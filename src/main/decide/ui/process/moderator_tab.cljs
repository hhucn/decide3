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
    [decide.models.process.mutations :as process.mutations]
    [decide.models.user :as user]
    [decide.ui.process.moderator.participant-list :as participant-list]
    [decide.routes :as routes]
    [mui.data-display :as dd]
    [mui.data-display.list :as list]
    [mui.inputs :as inputs]
    [mui.inputs.form :as form]
    [mui.layout :as layout]
    [mui.layout.grid :as grid]
    [mui.surfaces.card :as card]
    [mui.surfaces.accordion :as accordion]
    ["@mui/icons-material/Clear" :default ClearIcon]
    ["@mui/icons-material/ExpandMore" :default ExpandMoreIcon]
    ["@mui/icons-material/RemoveCircleOutline" :default RemoveCircleIcon]
    [decide.models.user.ui :as user.ui]
    [taoensso.timbre :as log]
    [mui.lab :as lab]))

(defn- accordion [{:keys [title]} body]
  (accordion/accordion {:defaultExpanded true}
    (accordion/summary {:expandIcon (dom/create-element ExpandMoreIcon)}
      (dd/typography {:variant "body1"} title))
    (accordion/details {} body)))

(defsc Moderator [_ {::user/keys [id display-name]
                     :keys [root/current-session >/avatar] :as props} {:keys [onDelete]}]
  {:query [::user/id ::user/display-name
           {:>/avatar (comp/get-query user.ui/Avatar)}
           {[:root/current-session '_] 1}]}                 ; TODO Replace join with Session.

  (let [self? (= id (::user/id current-session))]
    (list/item {}
      (list/item-avatar {}
        (user.ui/ui-avatar avatar))
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
             (comp/transact! this [(process.mutations/add-moderator {::process/slug slug ::user/email new-moderator-email})]))}
          (dd/typography {:variant :h6} (i18n/tr "Add moderator"))
          (inputs/textfield
            {:label (i18n/tr "Email")
             :value new-moderator-email
             :onChange (fn [e]
                         (let [value (evt/target-value e)]
                           (set-new-moderator-email value)
                           (when (< 2 (count value))
                             (df/load! this :autocomplete/users Moderator {:params {:term value}
                                                                           :target [:abc]}))))
             :fullWidth true
             :InputProps {:endAdornment (inputs/button {:type :submit} (i18n/trc "Submit new moderator form" "Add"))}}))))))

(def ui-moderator-list (comp/computed-factory ModeratorList))

(def default-input-props
  {:fullWidth true
   :autoComplete "off"
   :margin "normal"})

(defn- dissoc-equal-vals
  "Dissocs all keys from `m1` that have the same value in `m2` or aren't present."
  [m1 m2]
  (reduce-kv
    (fn [m k v]
      (if (and (contains? m2 k) (= v (k m2)))
        m
        (assoc m k v)))
    {}
    m1))

(defsc ProcessEdit [this {::process/keys [slug title description end-time type] :as props}]
  {:query [::process/slug ::process/title ::process/description ::process/start-time ::process/end-time ::process/type :process/features]
   :ident ::process/slug
   :use-hooks? true}
  (let [[form-state set-form-state] (hooks/use-state props)
        dirty? (not= form-state props)]
    (accordion {:title (i18n/tr "Edit process")}
      (grid/container
        {:component :form
         :spacing 1
         :onSubmit
         (fn [evt]
           (evt/prevent-default! evt)
           (when dirty?
             (comp/transact! this [(process.mutations/update-process
                                     ;; calculate diff ;; NOTE have a look at clojure.data/diff
                                     (merge (dissoc-equal-vals form-state props) {::process/slug slug}))])))}

        (grid/item {:xs 12}
          (inputs/textfield
            (merge default-input-props
              {:label (i18n/trc "Title of a process" "Title")
               :value (::process/title form-state)
               :helperText (when (not= title (::process/title form-state)) (i18n/tr "Edited"))
               :onChange #(set-form-state (assoc form-state ::process/title (evt/target-value %)))
               :inputProps {:maxLength 140}})))

        (grid/item {:xs 12}
          (inputs/textfield
            (merge default-input-props
              {:label (i18n/trc "Description of a process" "Description")
               :helperText (when (not= description (::process/description form-state)) (i18n/tr "Edited"))
               :multiline true
               :rows 7
               :value (::process/description form-state)
               :onChange #(set-form-state (assoc form-state ::process/description (evt/target-value %)))})))

        (grid/item {:xs 12}
          (form/group {:row true}
            (let [current-type (::process/type form-state)]
              (form/control-label
                {:label (i18n/tr "Is the process public?")
                 :checked (= current-type ::process/type.public)
                 :onChange #(set-form-state (assoc form-state ::process/type (if (= current-type ::process/type.public) ::process/type.private ::process/type.public)))
                 :control (inputs/checkbox {})}))))

        (grid/container {:item true :xs 12 :spacing 2}
          (grid/item {:xs 12 :sm 6}
            (lab/date-time-picker
              {:renderInput #(inputs/textfield (merge (js->clj %)
                                                 default-input-props
                                                 {:helperText (i18n/tr "Optional")}))
               :value (or (::process/start-time form-state) js/undefined)
               :maxDate (or (::process/end-time form-state) js/undefined)
               :onChange #(set-form-state (assoc form-state ::process/start-time %))
               :clearable true
               :label (i18n/trc "Start of a process" "Start")}))

          (grid/item {:xs 12 :sm 6}
            (lab/date-time-picker
              {:renderInput #(inputs/textfield (merge (js->clj %)
                                                 default-input-props
                                                 {:helperText (i18n/tr "Optional")}))
               :value (or (::process/end-time form-state) js/undefined)
               :maxDate (or (::process/start-time form-state) js/undefined)
               :onChange #(set-form-state (assoc form-state ::process/end-time %))
               :clearable true
               :label (i18n/trc "End of a process" "End")})))

        (grid/item {:xs 12}
          (accordion/accordion {:variant :outlined}
            (accordion/summary {:expandIcon (dom/create-element ExpandMoreIcon)}
              (i18n/tr "Advanced"))
            (accordion/details {}
              (grid/container {}
                (grid/item {:xs 12}
                  (form/group {:row true}
                    (form/control {:component :fieldset}
                      (form/label {:component :legend}
                        (i18n/tr "Features"))
                      (for [{:keys [key label help]}
                            [{:key :process.feature/single-approve
                              :label (i18n/tr "Single approval")
                              :help (i18n/tr "Participants can approve at most one proposal")}
                             {:key :process.feature/voting.public
                              :label (i18n/tr "Public votes")
                              :help (i18n/tr "Everyone can see who voted for what")}
                             #_{:key :process.feature/rejects :label (i18n/tr "Rejects") :help (i18n/tr "Participants can reject proposals.")}]
                            :let [active? (contains? (:process/features form-state) key)]] ; TODO Move somewhere sensible
                        (comp/fragment {:key key}
                          (form/helper-text {} help)
                          (form/group {:row true}
                            (form/control-label
                              {:label label
                               :control
                               (inputs/checkbox
                                 {:checked active?
                                  :onChange
                                  #(set-form-state
                                     (update form-state
                                       :process/features (if active? disj conj) key))})})))))))))))

        (grid/item {:xs 12}
          (inputs/button
            {:color :primary
             :type "submit"
             :disabled (not dirty?)}
            (i18n/trc "Submit form" "Submit")))))))

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
      (df/load! app process-ident participant-list/ParticipantList {:target (conj ref :participant-list)})
      ;; combine loads of same entity into one.
      (df/load! app process-ident Process
        {:target (targeting/multiple-targets
                   (conj ref :moderator-list)
                   (conj ref :process)
                   (conj ref :process-edit))
         :post-mutation `dr/target-ready
         :post-mutation-params {:target ref}}))))

(defsc ProcessModeratorTab [this {:keys [moderator-list process-edit participant-list process] :as props}]
  {:query [{:process (comp/get-query Process)}
           {:process-edit (comp/get-query ProcessEdit)}
           {:moderator-list (comp/get-query ModeratorList)}
           {:participant-list (comp/get-query participant-list/ParticipantList)}]
   :ident (fn [] [::ProcessModeratorTab (::process/slug process)])
   :route-segment (routes/segment ::routes/process-moderation)
   :will-enter
   (fn [app {:process/keys [slug]}]
     (let [ident (comp/get-ident ProcessModeratorTab {:process {::process/slug slug}})]
       (dr/route-deferred ident
         #(comp/transact! app [(init-moderator-tab {:slug slug})] {:ref ident}))))}
  (layout/container {}
    (layout/box {:my 2}
      (grid/container {:spacing 2}
        (grid/item {:xs 12 :md 8}
          (ui-process-edit process-edit))
        (when participant-list
          (grid/item {:xs 12 :sm 6 :md 4}
            (card/card {}
              (card/header {:title (i18n/trc "Label for list of participants" "Participants")})
              (card/content {}
                (participant-list/ui-participant-list participant-list)))))
        (grid/item {}
          (ui-moderator-list moderator-list))))))