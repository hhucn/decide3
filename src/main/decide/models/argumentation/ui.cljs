(ns decide.models.argumentation.ui
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [decide.models.argumentation :as argumentation]
    [decide.models.argumentation.api :as argumentation.api]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]
    [material-ui.data-display :as dd]
    [material-ui.data-display.list :as list]
    [material-ui.inputs :as inputs]
    [material-ui.lab.toggle-button :as toggle]
    [material-ui.layout :as layout]
    [material-ui.layout.grid :as grid]
    [material-ui.surfaces :as surfaces]
    [material-ui.transitions :as transitions]
    ["@material-ui/icons/ExpandMore" :default ExpandMore]
    ["@material-ui/icons/ExpandLess" :default ExpandLess]
    ["@material-ui/icons/Send" :default Send]
    ["@material-ui/icons/Close" :default Close]
    ["@material-ui/icons/AddCircleOutline" :default AddCircleOutline]))

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

(defn type-selector [{}])

(defn new-argument-ui [this {:keys [onSubmit type?] :or {type? false}}]
  (let [[new-argument-open? set-argument-open] (hooks/use-state false)
        [new-argument set-new-argument] (hooks/use-state "")
        [attitude set-attitude] (hooks/use-state :neutral)]
    (if (and new-argument-open? (comp/shared this :logged-in?))
      (surfaces/card
        {:component :form
         :onSubmit (fn [e]
                     (evt/prevent-default! e)
                     (onSubmit new-argument attitude)
                     (set-argument-open false)
                     (set-new-argument ""))}
        (surfaces/card-header
          {:title "New Argument"
           :action (inputs/icon-button {:onClick #(set-argument-open false)}
                     (dom/create-element Close))})
        (surfaces/card-content {}
          (grid/container
            {:spacing 1}

            (when type?
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
                  (toggle/button {:value :pro} (i18n/trc "Label for type selection" "Pro"))
                  (toggle/button {:value :neutral} (i18n/trc "Label for type selection" "Neutral"))
                  (toggle/button {:value :contra} (i18n/trc "Label for type selection" "Contra")))))

            (grid/item {:xs 12}
              (inputs/textfield
                {:type :text
                 :label (i18n/tr "New argument")
                 :fullWidth true
                 :color :secondary
                 :multiline true
                 :autoFocus true
                 :variant :filled
                 :value new-argument
                 :onKeyDown #(when (evt/escape? %) (set-argument-open false))
                 :onChange #(set-new-argument (evt/target-value %))}))))
        (surfaces/card-actions {}
          (inputs/button {:type :submit, :color :secondary, :startIcon (dom/create-element Send)}
            (i18n/tr "Submit"))))


      (dd/tooltip
        {:title (if (comp/shared this :logged-in?) "" (i18n/tr "Login to add argument"))
         :arrow true}
        (dom/span {}
          (inputs/button
            {:onClick #(set-argument-open true)
             :color :secondary
             :disabled (not (comp/shared this :logged-in?))
             :startIcon (dom/create-element AddCircleOutline)}
            (i18n/tr "Add argument")))))))

(declare ui-argument)

(defsc Argument [this {:argument/keys [type premise premise->arguments no-of-arguments]}
                 {:keys [type-feature?]}]
  {:query [:argument/id
           :argument/type
           {:argument/premise (comp/get-query Statement)}
           {:argument/premise->arguments '...}
           :argument/no-of-arguments]
   :ident :argument/id
   :use-hooks? true}
  (let [[show-premises? set-show-premises] (hooks/use-state false)]
    (layout/box {:borderTop 1
                 :borderColor "grey.100"}
      (list/item
        {:button true
         :onClick
         (fn toggle-list [_]
           (when-not show-premises? (df/refresh! this))     ; only refresh when going to show list
           (set-show-premises (not show-premises?)))}
        (when type-feature?
          (layout/box {:px 1 :clone true}
            (dd/typography {:variant :caption :color :textSecondary}
              (case type
                :pro (i18n/trc "Argument type" "Pro")
                :contra (i18n/trc "Argument type" "Contra")
                (i18n/trc "Argument type" "Neutral")))))
        (ui-statement premise)
        (when (pos? no-of-arguments)
          (dd/typography {:variant :caption :color :textSecondary} (str "(" no-of-arguments ")")))
        (if show-premises?
          (dom/create-element ExpandLess #js {:color "disabled"})
          (dom/create-element ExpandMore #js {:color "disabled"})))

      (layout/box {:ml 1}
        (transitions/collapse {:in show-premises?}
          (new-argument-ui this
            {:type? type-feature?
             :onSubmit
             (fn [statement type]
               (comp/transact! this
                 [(argumentation.api/add-argument-to-statement
                    {:conclusion premise
                     :argument
                     (-> {:argument/type (when-not (= :neutral type) type)}
                       argumentation/make-argument
                       (assoc :argument/premise
                              (argumentation/make-statement
                                {:statement/content statement})))})]))})
          (layout/box {:ml 1}
            (list/list {}
              (map
                (fn [argument]
                  (ui-argument argument {:type-feature? type-feature?}))
                premise->arguments))))))))

(def ui-argument (comp/computed-factory Argument {:keyfn :argument/id}))

(defsc ArgumentList [this {::proposal/keys [id positions]}]
  {:query [::proposal/id
           {::proposal/positions (comp/get-query Argument)}]
   :ident ::proposal/id
   :use-hooks? true}
  (let [type-feature? true]
    (comp/fragment
      (new-argument-ui this
        {:type? type-feature?
         :onSubmit
         (fn [statement type]
           (comp/transact! this
             [(argumentation.api/add-argument-to-proposal
                {:proposal {::proposal/id id}
                 :argument
                 (-> {:argument/type (when-not (= :neutral type) type)}
                   argumentation/make-argument
                   (assoc :argument/premise
                          (argumentation/make-statement
                            {:statement/content statement})))})]))})
      (list/list {}
        (map
          (fn [position]
            (ui-argument position {:type-feature? type-feature?}))
          positions)))))


(def ui-argument-list (comp/factory ArgumentList {:keyfn ::proposal/id}))