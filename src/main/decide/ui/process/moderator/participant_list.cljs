(ns decide.ui.process.moderator.participant-list
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [decide.models.process :as process]
    [decide.models.user :as user]
    [decide.models.user.ui :as user.ui]
    [decide.models.process.mutations :as process.api]
    [mui.data-display.list :as list]
    [mui.inputs :as inputs]
    [mui.lab :as lab]
    [mui.layout.grid :as grid]
    [com.fulcrologic.fulcro.dom :as dom]
    ["@mui/icons-material/RemoveCircleOutline" :default RemoveIcon]))

(defsc UserOption [this {::user/keys [display-name] :keys [user/nickname >/avatar]}]
  {:query [::user/id ::user/display-name :user/nickname
           {:>/avatar (comp/query user.ui/Avatar)}]
   :ident ::user/id}
  (comp/fragment
    (list/item-avatar {} (user.ui/ui-avatar avatar))
    (list/item-text {:primary display-name
                     :secondary (str "@" nickname)})))

(def ui-user-option (comp/factory UserOption {:keyfn ::user/id}))

(defsc UserAutocomplete [this {:keys [form/id ui/user ui/inputValue ui/options] :as props}
                         {:keys [onEnter] :or {onEnter #()}}]
  {:query [:form/id
           :ui/user
           :ui/inputValue
           {:ui/options (comp/get-query UserOption)}
           [df/marker-table '_]]
   :ident :form/id
   :initial-state
   {:form/id :param/id
    :ui/user nil
    :ui/inputValue ""
    :ui/options []}}
  (let [loading? (df/loading? (get props [df/marker-table [::UserAutocomplete id]]))]
    (lab/autocomplete
      {:options options
       :value user
       :inputValue inputValue
       :fullWidth true
       :autoComplete true
       :autoHighlight true
       ; :clearOnEscape true
       :loading loading?
       :getOptionLabel (fn [option] (::user/display-name option))
       :isOptionEqualToValue =

       :renderOption
       (fn [props option _state]
         (dom/li props
           (comp/with-parent-context this
             (ui-user-option option))))

       :onChange
       (fn [_e value _reason]
         (when value (onEnter value))
         (comp/transact!! this [(m/set-props {:ui/value value :ui/inputValue ""})]))

       :onInputChange
       (fn [_e inputValue _reason]
         (m/set-string!! this :ui/inputValue :value inputValue)
         (df/load! this :autocomplete/users UserOption
           {:params {:term inputValue :limit 3}
            :marker [::UserAutocomplete id]
            :target (conj (comp/get-ident this) :ui/options)}))

       :renderInput
       (fn [params]
         ; js->clj seems to break things..
         (let [cljs-props {:label (i18n/tr "Participant")
                           :variant :outlined}]
           (inputs/textfield (js/Object.assign params (clj->js cljs-props)))))})))


(def ui-user-autocomplete (comp/computed-factory UserAutocomplete {:keyfn :form/id}))

(defsc Participant [this {::user/keys [display-name]
                          :keys [>/avatar user/nickname]}
                    {remove! :onRemove}]
  {:query [::user/id
           ::user/display-name
           :user/nickname
           {:>/avatar (comp/get-query user.ui/Avatar)}]
   :ident ::user/id}
  (list/item {}
    (list/item-avatar {}
      (user.ui/ui-avatar avatar))
    (list/item-text
      {:primary display-name
       :secondary (str "@" nickname)})
    (list/item-secondary-action {}
      (inputs/icon-button {:edge "end"
                           :aria-label (i18n/trc "[aria] remove participant" "remove")
                           :onClick #(remove! (comp/get-ident this))}
        (dom/create-element RemoveIcon)))))

(def ui-participant (comp/computed-factory Participant {:keyfn ::user/id}))

(defsc ParticipantList [this {::process/keys [slug participants]
                              :keys [ui/add-participant-form]}]
  {:query [::process/slug
           {::process/participants (comp/get-query Participant)}
           {:ui/add-participant-form (comp/get-query UserAutocomplete)}]
   :ident ::process/slug
   :pre-merge
   (fn [{:keys [normalized-state data-tree]}]
     (merge
       normalized-state
       data-tree
       {:ui/add-participant-form (comp/get-initial-state UserAutocomplete {:id ::ParticipantList})}))}
  (grid/container {}
    (grid/item {:xs 12}
      (ui-user-autocomplete add-participant-form
        {:onEnter
         (fn [user]
           (comp/transact! this [(process.api/add-participant {::user/id (::user/id user)
                                                               ::process/slug slug})]))}))
    (grid/item {:xs 12}
      (list/list {:style {:maxHeight "500px" :overflowY "auto"}}
        (mapv
          #(ui-participant %
             {:onRemove
              (fn [[_ id]] (comp/transact! this [(process.api/remove-participant
                                                   {::user/id id
                                                    ::process/slug slug})]))})
          participants)))))

(def ui-participant-list (comp/factory ParticipantList {:keyfn ::process/slug}))

(defmutation init-participant-list [{:keys [slug]}]
  (action [{:keys [app]}]
    (df/load! app [::process/slug slug] ParticipantList)))