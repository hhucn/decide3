(ns decide.ui.argumentation
  (:require
   [com.fulcrologic.fulcro-i18n.i18n :as i18n]
   [com.fulcrologic.fulcro.algorithms.merge :as mrg]
   [com.fulcrologic.fulcro.application :as app]
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
   [decide.ui.user :as user.ui]
   [mui.data-display :as dd]
   [mui.feedback.skeleton :refer [skeleton]]
   [mui.inputs :as inputs]
   [mui.inputs.toggle-button :as toggle]
   [mui.layout :as layout]
   [mui.layout.grid :as grid]
   [mui.surfaces :as surfaces]
   [mui.surfaces.card :as card]
   [mui.transitions :as transitions]
   ["@mui/icons-material/AddCircleOutline" :default AddCircleOutline]
   ["@mui/icons-material/AddComment" :default AddComment]
   ["@mui/icons-material/Comment" :default Comment]
   ["@mui/icons-material/ExpandLess" :default ExpandLess]
   ["@mui/icons-material/ExpandMore" :default ExpandMore]
   ["@mui/icons-material/Send" :default Send]))

(defn ui-argument-placeholder [_]
  (layout/box {:mx 4 :my 1}
    (grid/container {:spacing 1}
      (grid/item {:container true :spacing 1}
        (grid/item {} (skeleton {:variant :circle :width "24px" :height "24px"}))
        (grid/item {} (skeleton {:width 60})))
      (grid/item {:xs 12}
        (skeleton {})
        (skeleton {:width "60%"})))))

(defn ui-argument-list-placeholder [{:keys [n]}]
  (grid/container {:spacing 2}
    (mapv #(grid/item {:key % :xs 12} (ui-argument-placeholder {}))
      (range (or n 3)))))


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
  content
  #_(list/item-text {:primary content
                     :secondary (when author (ui-statement-author author))}))

(def ui-statement (comp/factory Statement {:keyfn :statement/id}))

(defn type-label [type]
  (case type
    :pro (i18n/trc "Argument type" "Pro")
    :contra (i18n/trc "Argument type" "Contra")
    (i18n/trc "Argument type" "Neutral")))

(defsc NewArgumentFormCard [this {:keys [new-argument attitude]} {:keys [onSave onClose use-type?]}]
  {:query [:belongs-to :new-argument :attitude]
   :ident [::NewArgumentForm :belongs-to]
   :initial-state
   {:belongs-to :param/belongs-to
    :new-argument ""
    :attitude :neutral}}
  (card/card
    {:component :form
     :onSubmit
     (fn [e]
       (evt/prevent-default! e)
       (onSave new-argument attitude)
       (onClose)
       (comp/transact!! this [(m/set-props {:new-argument "", :attitude :neutral})] {:compressible? true}))}

    (card/content {}
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
             :value (or new-argument "")
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
    (card/actions {}
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


(defn type-indicator [type]
  (when (#{:pro :contra} type)
    (dd/chip {:label (type-label type)
              :size :small
              :sx {:mr 1}
              :color (case type
                       :pro :success
                       :contra :error
                       :default)})))

(defn argument-header [{:keys [author onClick show-premises?]}]
  (card/header
    {:avatar (user.ui/chip author)
     :action (inputs/icon-button
               {:size :small
                :onClick onClick
                :disabled (nil? onClick)}
               (dom/create-element (if show-premises? ExpandLess ExpandMore)))}))

(defn argument-content [{:argument/keys [type premise]}]
  (layout/box {:ml 3 :px 2 :py 0.5}                         ; TODO Scale this with viewport size?
    (dd/typography {:variant :body2, :color :textPrimary, :component :span}
      (type-indicator type)
      (ui-statement premise))))

(declare ui-argument)

(defsc Argument [this {:argument/keys [id type premise premise->arguments no-of-arguments]
                       :keys [ui/new-argument-form] :as props}
                 {:keys [type-feature?]}]
  {:query [:argument/id
           :argument/type
           {:argument/premise (comp/get-query Statement)}
           {:argument/premise->arguments '...}
           :argument/no-of-arguments
           {:ui/new-argument-form (comp/get-query NewArgumentFormCard)}
           [df/marker-table '_]]
   :ident :argument/id
   :use-hooks? true}
  (let [[show-premises? set-show-premises] (hooks/use-state false)
        [new-argument-open? set-new-argument-open] (hooks/use-state false)
        loading? (df/loading? (get-in props [df/marker-table [::argument-premises id]]))
        toggle-list! (fn toggle-list [_]
                       (if loading?
                         (app/abort! this [::argument-premises id])
                         ;; only refresh when going to show list
                         (when-not show-premises?
                           (df/refresh! this
                             {:refresh [(comp/get-ident this)]
                              :parallel true
                              :marker [::argument-premises id]
                              :abort-id [::argument-premises id]})))
                       (set-show-premises (not show-premises?)))

        {:statement/keys [author]} premise]
    (card/card {:variant :outlined
                :component :article}

      (argument-header {:argument props
                        :author author
                        :show-premises? show-premises?
                        :onClick (when (pos? no-of-arguments) toggle-list!)})

      (argument-content props)

      (card/actions {}
        (inputs/button
          {:size :small
           :variant :label
           :startIcon (dom/create-element Comment)
           :onClick toggle-list!}
          (str no-of-arguments))
        (dd/tooltip
          {:title (if (comp/shared this :logged-in?) "" (i18n/tr "Login to add argument"))}
          (dom/span {}
            (inputs/button
              {:onClick (fn []
                          (when (not new-argument-open?)    ; about to open?
                            (comp/transact! this [(init-new-argument-form {:belongs-to [:argument/id id]})]))
                          (set-new-argument-open (not new-argument-open?)))
               :variant :text
               :disabled (not (comp/shared this :logged-in?))
               :endIcon (dom/create-element AddComment)}
              (i18n/tr "Reply")))))

      (transitions/collapse {:in (and new-argument-open? new-argument-form)}
        (when new-argument-form
          (card/content {}
            (ui-new-argument-form-card new-argument-form
              {:use-type? type-feature?
               :onClose #(set-new-argument-open false)
               :onSave (fn [statement-str type]
                         (let [new-statement (argumentation/make-statement {:statement/content statement-str})]
                           (comp/transact! this
                             [(argumentation.api/add-argument-to-statement
                                {:conclusion premise
                                 :argument
                                 (-> {:argument/type (when-not (= :neutral type) type)}
                                   argumentation/make-argument
                                   (assoc :argument/premise new-statement))})])))}))))

      (transitions/collapse {:in show-premises?
                             :mountOnEnter true}
        (layout/box {:ml 1}
          (grid/container {:spacing 1 :direction :column}
            (if (and loading? (empty? premise->arguments))
              (ui-argument-list-placeholder {:n (min no-of-arguments 3)})
              (mapv
                (fn [argument]
                  (grid/item {:key (:argument/id argument)}
                    (ui-argument argument {:type-feature? type-feature?})))
                premise->arguments))))))))

(def ui-argument (comp/computed-factory Argument {:keyfn :argument/id}))




(defsc ArgumentList [this {::proposal/keys [id positions]
                           :keys [ui/new-argument-form]
                           :as props}]
  {:ident ::proposal/id
   :query [::proposal/id
           {::proposal/positions (comp/get-query Argument)}
           {:ui/new-argument-form (comp/get-query NewArgumentFormCard)}
           [df/marker-table '_]]
   :initial-state (fn [{id ::proposal/id}]
                    {::proposal/id id
                     ::proposal/positions []})
   :use-hooks? true}
  (let [loading?           (df/loading? (get-in props [df/marker-table [:load-argument-section (comp/get-ident this)]]))
        empty-and-loading? (and (empty? positions) loading?)
        type-feature?      true                             ; types are pro/contra/neutral
        [new-argument-open? set-new-argument-open!] (hooks/use-state false)]
    (grid/container
      {:spacing 1}
      (grid/item {:xs 12}
        (surfaces/toolbar {:variant :dense, :disableGutters true, :style {:justifyContent :space-between}}
          (dom/div {}
            (inputs/button
              {:variant :outlined
               :color :primary
               :onClick (fn []
                          (when (not new-argument-open?)    ; about to open?
                            (comp/transact! this [(init-new-argument-form {:belongs-to [::proposal/id id]})]))
                          (set-new-argument-open! (not new-argument-open?)))
               :disabled (or (not (comp/shared this :logged-in?))
                           empty-and-loading?)
               :startIcon (dom/create-element AddComment)}
              (i18n/tr "Add argument")))
          (dom/div {}))
        (transitions/collapse {:in (and new-argument-open? new-argument-form)}
          (when new-argument-form
            (ui-new-argument-form-card new-argument-form
              {:use-type? type-feature?
               :onClose #(set-new-argument-open! false)     ; THOUGHT This is stupid. Call it onAbort or something..
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
        (ui-argument-list-placeholder {:n 5})
        (mapv
          (fn [position]
            (grid/item {:key (:argument/id position)
                        :xs 12}
              (ui-argument position {:type-feature? type-feature?})))
          positions)))))


(def ui-argument-list (comp/factory ArgumentList {:keyfn ::proposal/id}))