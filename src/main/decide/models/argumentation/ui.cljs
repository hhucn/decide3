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
    [material-ui.data-display.list :as list]
    [material-ui.inputs :as inputs]
    [material-ui.layout :as layout]
    [material-ui.transitions :as transitions]
    ["@material-ui/icons/ExpandMore" :default ExpandMore]
    ["@material-ui/icons/ExpandLess" :default ExpandLess]
    ["@material-ui/icons/Send" :default Send]
    ["@material-ui/icons/AddCircleOutline" :default AddCircleOutline]
    [material-ui.data-display :as dd]))

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

(defn new-argument-ui [this {:keys [onSubmit]}]
  (let [[new-argument-open? set-argument-open] (hooks/use-state false)
        [new-argument set-new-argument] (hooks/use-state "")]
    (if (and new-argument-open? (comp/shared this :logged-in?))
      (dom/form
        {:onSubmit (fn [e]
                     (evt/prevent-default! e)
                     (onSubmit new-argument)
                     (set-argument-open false)
                     (set-new-argument ""))}

        (inputs/textfield
          {:type :text
           :label (i18n/tr "New argument")
           :fullWidth true
           :color :secondary
           :size :small
           :autoFocus true
           :variant :filled
           :value new-argument
           :onKeyDown #(when (evt/escape? %) (set-argument-open false))
           :onChange #(set-new-argument (evt/target-value %))})
        (inputs/button {:type :submit, :color :secondary, :startIcon (dom/create-element Send)}
          (i18n/tr "Submit"))
        (inputs/button {:onClick #(set-argument-open false)} (i18n/tr "Cancel")))

      (inputs/button
        {:onClick #(set-argument-open true)
         :color :secondary
         :disabled (not (comp/shared this :logged-in?))
         :startIcon (dom/create-element AddCircleOutline)}
        (i18n/tr "Add argument")))))

(declare ui-argument)

(defsc Argument [this {:argument/keys [premise premise->arguments no-of-arguments]}]
  {:query [:argument/id
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
        #_(dom/create-element ArrowRight #js {:fontSize "small" :color "disabled"})
        (ui-statement premise)
        (when (pos? no-of-arguments)
          (dd/typography {:variant :caption :color :textSecondary} (str "(" no-of-arguments ")")))
        (if show-premises?
          (dom/create-element ExpandLess #js {:color "disabled"})
          (dom/create-element ExpandMore #js {:color "disabled"})))

      (layout/box {:ml 1}
        (transitions/collapse {:in show-premises?}
          (new-argument-ui this
            {:onSubmit
             (fn [statement]
               (comp/transact! this
                 [(argumentation.api/add-argument-to-statement
                    {:conclusion premise
                     :argument
                     (argumentation/make-argument-with-premise {:statement/content statement})})]))})
          (layout/box {:ml 1}
            (list/list {}
              (map ui-argument premise->arguments))))))))

(def ui-argument (comp/factory Argument {:keyfn :argument/id}))

(defsc ArgumentList [this {::proposal/keys [id positions]}]
  {:query [::proposal/id
           {::proposal/positions (comp/get-query Argument)}]
   :ident ::proposal/id
   :use-hooks? true}
  (comp/fragment
    (new-argument-ui this
      {:onSubmit
       (fn [statement]
         (comp/transact! this [(argumentation.api/add-argument-to-proposal
                                 {:proposal {::proposal/id id}
                                  :argument
                                  (argumentation/make-argument-with-premise {:statement/content statement})})]))})
    (list/list {}
      (map ui-argument positions))))


(def ui-argument-list (comp/factory ArgumentList {:keyfn ::proposal/id}))