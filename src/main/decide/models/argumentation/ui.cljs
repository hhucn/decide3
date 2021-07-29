(ns decide.models.argumentation.ui
  (:require
    [clojure.set :as set]
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.algorithms.merge :as mrg]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [decide.models.argumentation :as argumentation]
    [decide.models.argumentation.api :as argumentation.api]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]
    [decide.models.user.ui :as user.ui]
    [material-ui.data-display :as dd]
    [material-ui.data-display.list :as list]
    [material-ui.inputs :as inputs]
    [material-ui.lab :refer [skeleton]]
    [material-ui.lab.toggle-button :as toggle]
    [material-ui.layout :as layout]
    [material-ui.layout.grid :as grid]
    [material-ui.surfaces :as surfaces]
    [material-ui.transitions :as transitions]
    ["@material-ui/icons/ExpandLess" :default ExpandLess]
    ["@material-ui/icons/ExpandMore" :default ExpandMore]
    ["@material-ui/icons/Send" :default Send]
    ["@material-ui/icons/Close" :default Close]
    ["@material-ui/icons/AddCircleOutline" :default AddCircleOutline]
    ["@material-ui/icons/Comment" :default Comment]
    ["@material-ui/icons/AddComment" :default AddComment]))

(defsc StatementAuthor [_ {::user/keys [display-name]}]
  {:query [::user/id ::user/display-name]
   :ident ::user/id}
  (i18n/trf "by {author}" {:author display-name}))

(def ui-statement-author (comp/factory StatementAuthor {:keyfn ::user/id}))

(defsc Statement [_ {:statement/keys [content author]}]
  {:query [:statement/id
           :statement/content
           {:statement/author (comp/get-query user/User)}]
   :ident :statement/id}
  (list/item-text {:primary content
                   :secondary (when author (ui-statement-author author))}))

(def ui-statement (comp/factory Statement {:keyfn :statement/id}))

(defn type-label [type]
  (case type
    :pro (i18n/trc "Argument type" "Pro")
    :contra (i18n/trc "Argument type" "Contra")
    (i18n/trc "Argument type" "Neutral")))

(defn new-argument-ui [{:keys [save! close! use-type?] :or {use-type? false}}]
  (let [[new-argument set-new-argument] (hooks/use-state "")
        [attitude set-attitude] (hooks/use-state :neutral)]
    (surfaces/card
      {:component :form
       :onSubmit
       (fn [e]
         (evt/prevent-default! e)
         (save! new-argument attitude)
         (close!)
         (set-new-argument ""))}

      (surfaces/card-content {}
        (grid/container
          {:spacing 1}

          (grid/item {:xs 12}
            (inputs/textfield
              {:type :text
               :label (i18n/tr "Argument")
               :fullWidth true
               :color :secondary
               :multiline true
               :autoFocus true
               :variant :filled
               :value new-argument
               :onKeyDown #(when (evt/escape? %) (close!))
               :onChange #(set-new-argument (evt/target-value %))
               :inputProps {:required true}}))

          (when use-type?
            (grid/item {:xs 12 :sm :auto}
              (dd/typography {:component :legend
                              :variant :caption
                              :color :textSecondary}
                (i18n/tr "What is the attitude of this argument?"))
              (toggle/button-group
                {:size :small
                 :exclusive true
                 :value (name attitude)
                 :onChange #(some->> %2 keyword set-attitude)}
                (for [type [:pro :neutral :contra]]
                  (toggle/button {:key type :value type} (type-label type))))))))
      (surfaces/card-actions {}
        (inputs/button {:type :submit,
                        :variant :contained
                        :color :secondary,
                        :startIcon (dom/create-element Send)}
          (i18n/tr "Add"))
        (inputs/button {:onClick close!} (i18n/tr "Cancel"))))))

(defsc NewArgumentFormCard [this {:keys [new-argument attitude]} {:keys [onSave onClose use-type?]}]
  {:query [:belongs-to :new-argument :attitude]
   :ident [::NewArgumentForm :belongs-to]
   :initial-state
   {:belongs-to :param/belongs-to
    :new-argument ""
    :attitude :neutral}}
  (surfaces/card
    {:component :form
     :onSubmit
     (fn [e]
       (evt/prevent-default! e)
       (onSave new-argument attitude)
       (onClose)
       (comp/transact! this [(m/set-props {:new-argument "", :attitude :neutral})] {:compressible? true}))}

    (surfaces/card-content {}
      (grid/container
        {:spacing 1}

        (grid/item {:xs 12}
          (inputs/textfield
            {:type :text
             :label (i18n/tr "Argument")
             :fullWidth true
             :color :secondary
             :multiline true
             :autoFocus true
             :variant :filled
             :value new-argument
             :onKeyDown #(when (evt/escape? %) (onClose))
             :onChange #(m/set-string!! this :new-argument :event %)
             :inputProps {:required true}}))

        (when use-type?
          (grid/item {:xs 12 :sm :auto}
            (dd/typography {:component :legend
                            :variant :caption
                            :color :textSecondary}
              (i18n/tr "What is the attitude of this argument?"))
            (toggle/button-group
              {:size :small
               :exclusive true
               :value (name attitude)
               :onChange #(some->> %2 keyword (m/set-value!! this :attitude))}
              (for [type [:pro :neutral :contra]]
                (toggle/button {:key type :value type} (type-label type))))))))
    (surfaces/card-actions {}
      (inputs/button {:type :submit,
                      :variant :contained
                      :color :secondary,
                      :startIcon (dom/create-element Send)}
        (i18n/tr "Add"))
      (inputs/button {:onClick onClose} (i18n/tr "Cancel")))))

(def ui-new-argument-form-card (comp/computed-factory NewArgumentFormCard {:keyfn :form/id}))

(defmutation init-new-argument-form [{:keys [belongs-to]}]
  (action [{:keys [state ref]}]
    (swap! state mrg/merge-component
      NewArgumentFormCard
      (comp/get-initial-state NewArgumentFormCard {:belongs-to belongs-to})
      :replace (conj ref :ui/new-argument-form))))

(declare ui-argument)

(defn type-indicator [type]
  (when (#{:pro :contra} type)
    (layout/box {:clone true :mr 1}
      (dd/chip {:label (type-label type)
                :size :small
                :color (case type
                         :pro :primary
                         :contra :secondary
                         :default)}))))


(defsc Argument [this {:argument/keys [type premise premise->arguments no-of-arguments]}
                 {:keys [type-feature?]}]
  {:query [:argument/id
           :argument/type
           {:argument/premise (comp/get-query Statement)}
           {:argument/premise->arguments '...}
           :argument/no-of-arguments]
   :ident :argument/id
   :use-hooks? true}
  (let [[show-premises? set-show-premises] (hooks/use-state false)
        [new-argument-open? set-new-argument-open] (hooks/use-state false)
        toggle-list! (hooks/use-callback
                       (fn toggle-list [_]
                         (when-not show-premises? (df/refresh! this)) ; only refresh when going to show list
                         (set-show-premises (not show-premises?)))
                       [this show-premises?])
        {:statement/keys [author content]} premise]
    (layout/box {:clone true :mr "-1px" :mb "-1px"}
      (surfaces/card {:variant :outlined
                      :elevation 0
                      :square true
                      :component :article}

        (surfaces/card-header
          {:avatar (user.ui/chip (set/rename-keys author {::user/display-name :user/display-name
                                                          ::user/id :user/id}))
           :action (inputs/icon-button
                     {:size :small
                      :onClick toggle-list!
                      :disabled (zero? no-of-arguments)}
                     (if show-premises?
                       (dom/create-element ExpandLess)
                       (dom/create-element ExpandMore)))})

        (layout/box {:ml 3 :px 2 :py 0.5} ; TODO Scale this with viewport size?
          (dd/typography {:variant :body2, :color :textPrimary}
            (type-indicator type)
            content))

        (surfaces/card-actions {}
          (inputs/button
            {:size :small
             :startIcon (dom/create-element Comment)
             :onClick toggle-list!}
            (str no-of-arguments))
          (dd/tooltip
            {:title (if (comp/shared this :logged-in?) "" (i18n/tr "Login to add argument"))
             :arrow true}
            (dom/span {}
              (inputs/button
                {:onClick #(set-new-argument-open (not new-argument-open?))
                 :size :small
                 :disabled (not (comp/shared this :logged-in?))
                 :startIcon (dom/create-element AddComment)}
                (i18n/tr "Add argument")))))
        (transitions/collapse {:in new-argument-open?}
          (surfaces/card-content {}
            (new-argument-ui
              {:use-type? type-feature?
               :close! #(set-new-argument-open false)
               :save! (fn [statement-str type]
                        (let [new-statement (argumentation/make-statement {:statement/content statement-str})]
                          (comp/transact! this
                            [(argumentation.api/add-argument-to-statement
                               {:conclusion premise
                                :argument
                                (-> {:argument/type (when-not (= :neutral type) type)}
                                  argumentation/make-argument
                                  (assoc :argument/premise new-statement))})])))})))
        (when (seq premise->arguments)
          (transitions/collapse {:in show-premises?}
            (layout/box {:ml 1}
              (grid/container {:spacing 1 :direction :column}
                (mapv
                  (fn [argument]
                    (grid/item {:key (:argument/id argument)}
                      (ui-argument argument {:type-feature? type-feature?})))
                  premise->arguments)))))))))

(def ui-argument (comp/computed-factory Argument {:keyfn :argument/id}))

(def ui-argument-list-placeholder
  (comp/fragment
    (grid/item {:xs 12} (skeleton {:variant :rect :height "160px"}))
    (grid/item {:xs 12} (skeleton {:variant :rect :height "160px"}))
    (grid/item {:xs 12} (skeleton {:variant :rect :height "160px"}))))


(defsc ArgumentList [this {::proposal/keys [id positions]
                           :keys [ui/new-argument-form]
                           :as props}]
  {:query [::proposal/id
           {::proposal/positions (comp/get-query Argument)}
           {:ui/new-argument-form (comp/get-query NewArgumentFormCard)}
           [df/marker-table '_]]
   :ident ::proposal/id
   :use-hooks? true}
  (let [loading? (df/loading? (get-in props [df/marker-table [:load-argument-section (comp/get-ident this)]]))
        empty-and-loading? (and (empty? positions) loading?)
        type-feature? true
        [new-argument-open? set-new-argument-open!] (hooks/use-state false)]
    (grid/container
      {:spacing 1}
      (grid/item {:xs 12}
        (inputs/button
          {:onClick (fn []
                      (when (not new-argument-open?)        ; about to open?
                        (comp/transact! this [(init-new-argument-form {:belongs-to [::proposal/id id]})]))
                      (set-new-argument-open! (not new-argument-open?)))
           :disabled (or (not (comp/shared this :logged-in?))
                       empty-and-loading?)
           :startIcon (dom/create-element AddComment)}
          (i18n/tr "Add argument"))
        (transitions/collapse {:in (and new-argument-open? new-argument-form)}
          (when new-argument-form
            (ui-new-argument-form-card new-argument-form
              {:use-type? type-feature?
               :onClose #(set-new-argument-open! false)
               :onSave
               (fn [text type]
                 (let [statement (argumentation/make-statement {:statement/content text})]
                   (comp/transact! this
                     [(argumentation.api/add-argument-to-proposal
                        {:proposal {::proposal/id id}
                         :argument
                         (-> {:argument/type (when-not (= :neutral type) type)}
                           argumentation/make-argument
                           (assoc :argument/premise statement))})])))}))))
      (if empty-and-loading?
        ui-argument-list-placeholder
        (mapv
          (fn [position]
            (grid/item {:key (:argument/id position)
                        :xs 12}
              (ui-argument position {:type-feature? type-feature?})))
          positions)))))


(def ui-argument-list (comp/factory ArgumentList {:keyfn ::proposal/id}))