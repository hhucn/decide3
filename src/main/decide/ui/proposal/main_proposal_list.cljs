(ns decide.ui.proposal.main-proposal-list
  (:require
    [clojure.string :as str]
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.react.error-boundaries :as error-boundaries]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.ui.proposal.card :as proposal-card]
    [decide.ui.proposal.favorite-list :as favorite-list]
    [decide.ui.proposal.lists.hierarchy :as hierarchy-list]
    [decide.ui.proposal.plain-list :as plain-list]
    [decide.utils.breakpoint :as breakpoint]
    [decide.utils.time :as time]
    [mui.data-display :as dd]
    [mui.data-display.list :as list]
    [mui.feedback :as feedback]
    [mui.inputs :as inputs]
    [mui.inputs.form :as form]
    [mui.inputs.input :as input]
    [mui.inputs.toggle-button :as toggle]
    [mui.layout :as layout]
    [mui.layout.grid :as grid]
    [mui.navigation :as navigation]
    [mui.surfaces :as surfaces]
    ["@mui/icons-material/Add" :default AddIcon]
    ["@mui/icons-material/Refresh" :default Refresh]
    ["@mui/icons-material/ViewList" :default ViewList]
    ["@mui/icons-material/ViewModule" :default ViewModule]))


(defn add-proposal-fab [props]
  (layout/box
    {:position :fixed
     :bottom "16px"
     :right "16px"}
    (inputs/fab
      (merge
        {:aria-label (i18n/tr "New proposal")
         :title (i18n/tr "New proposal")
         :color :secondary
         :variant :extended}
        props)
      (dom/create-element AddIcon)
      (layout/box {:ml 1}
        (i18n/tr "New proposal")))))

(defn sort-selector [selected set-selected!]
  (form/control {:size :small}
    (input/label {:id "main-proposal-list-sort"} (i18n/trc "Label for sort order selection" "Sort"))
    (inputs/select
      {:value selected
       :onChange (fn [e]
                   (set-selected! (evt/target-value e)))
       :autoWidth true
       :labelId "main-proposal-list-sort"
       :label (i18n/trc "Label for sort order selection" "Sort")}
      (navigation/menu-item {:value "new->old"} (i18n/trc "Sort order option" "New → Old"))
      (navigation/menu-item {:value "old->new"} (i18n/trc "Sort order option" "Old → New"))
      (navigation/menu-item {:value "most-approvals"} (i18n/trc "Sort order option" "Approvals ↓")))))

(defn new-proposal-card [{:keys [disabled? onClick]}]
  (inputs/button {:style {:height "100%"
                          :borderStyle "dashed"}
                  :fullWidth true
                  :size :large
                  :disabled disabled?
                  :variant :outlined
                  :onClick onClick}
    (layout/box {:color (when disabled? "text.disabled") :mr 1 :component AddIcon})
    (if-not disabled?
      (i18n/tr "New proposal")
      (i18n/tr "Login to add new argument"))))

(defn main-list-toolbar [{:keys [left right]} & children]
  (apply surfaces/toolbar {:disableGutters true :variant :dense, :sx {:my 1}} children))

(defn info-toolbar-item [{:keys [label]}]
  (grid/item {}
    (dd/typography {:variant :overline} label)))

(defn layout-selector [{:keys [value onChange]} buttons]
  (when (< 1 (count buttons))
    (toggle/button-group
      {:exclusive true
       :size :small
       :value value
       :onChange (fn [_event new-layout]
                   (some-> new-layout keyword onChange))}
      (doall
        (for [[v icon] buttons]
          (toggle/button {:value v :key v}
            (dom/create-element icon)))))))


(declare MainProposalList)

(defmutation enter-proposal-list [params]
  (action [{:keys [app state ref]}]
    (if (get-in @state ref)                                 ; there was 'a' process loaded
      (do                                                   ;; target ready and refresh
        (dr/target-ready! app ref)
        (df/load! app ref MainProposalList {:parallel true :marker ::loading-proposals}))
      (do                                                   ;; load in two steps
        (df/load! app ref MainProposalList
          {:post-mutation `dr/target-ready
           :without #{::process/proposals :>/favorite-list}
           :post-mutation-params {:target ref}})
        (df/load! app ref MainProposalList
          {:focus [::process/slug ::process/proposals :>/favorite-list :>/hierarchy-list]
           :marker ::loading-proposals})))))

; TODO This component has become way to big.
; TODO Add empty state.
(defsc MainProposalList [this {::process/keys [slug proposals end-time no-of-participants no-of-proposals]
                               :keys [root/current-session >/favorite-list >/hierarchy-list process/features]
                               :as props}
                         {:keys [show-new-proposal-dialog]}]
  {:query [::process/slug
           {::process/proposals (comp/get-query proposal-card/ProposalCard)}
           ::process/no-of-proposals
           ::process/no-of-participants
           ::process/end-time
           :process/features

           {:>/favorite-list (comp/get-query favorite-list/FavoriteList)}
           {:>/hierarchy-list (comp/get-query hierarchy-list/HierarchyList)}

           [:root/current-session '_]
           [df/marker-table ::loading-proposals]]
   :ident ::process/slug
   :route-segment ["proposals"]
   :will-enter (fn [app {::process/keys [slug]}]
                 (let [ident (comp/get-ident MainProposalList {::process/slug slug})]
                   (dr/route-deferred ident
                     #(comp/transact! app [(enter-proposal-list {})] {:ref ident}))))
   :use-hooks? true}
  (let [logged-in? (get current-session :session/valid?)
        loading-proposals? (df/loading? (get props [df/marker-table ::loading-proposals]))
        [selected-sort set-selected-sort!] (hooks/use-state "most-approvals")
        [selected-layout set-selected-layout!] (hooks/use-state :favorite)
        sorted-proposals (hooks/use-memo #(proposal/rank-by selected-sort proposals) [selected-sort proposals])
        process-over? (and end-time (time/past? end-time))
        large-ui? (breakpoint/>=? "sm")]
    (comp/fragment
      (layout/container {:maxWidth :xl}

        ; sort / filter toolbar
        (main-list-toolbar {}
          ;; left side
          (grid/container {:spacing 2, :alignItems :center}
            (grid/item {}
              (inputs/button
                {:onClick #(df/refresh! this {:marker ::loading-proposals})
                 :variant :outlined
                 :disabled loading-proposals?
                 :startIcon
                 (if loading-proposals?
                   (feedback/circular-progress {:size "20px" :color :inherit})
                   (dom/create-element Refresh))}
                (i18n/trc "Reload content" "Refresh")))
            (info-toolbar-item
              {:label (i18n/trf "Proposals {count}" {:count (max no-of-proposals (count sorted-proposals))})})
            (info-toolbar-item
              {:label (i18n/trf "Participants {count}"
                        {:count (str (max no-of-participants 0))})}))

          ;; right side
          (grid/container
            {:spacing 2, :alignItems :center, :justifyContent :flex-end}
            (grid/item {}
              (layout-selector
                {:value selected-layout
                 :onChange set-selected-layout!}
                (cond-> {:favorite ViewModule}
                  large-ui? (assoc :hierarchy ViewList))))

            (grid/item {} (sort-selector selected-sort set-selected-sort!))))

        ; main list
        (error-boundaries/error-boundary
          (let [context {::process/slug slug
                         :process-over? process-over?
                         :card-props {:variant (when large-ui? :outlined)}}
                list-options (merge
                               {:items (mapv #(comp/computed % context) sorted-proposals)}
                               context)]
            (case selected-layout
              :favorite
              (grid/container {:spacing (if large-ui? 2 1)
                               :alignItems "stretch"
                               :style {:position "relative"}}
                (if (and (#{"most-approvals"} selected-sort) (not (empty? sorted-proposals)))
                  (favorite-list/ui-favorite-list favorite-list)
                  (plain-list/plain-list list-options))
                (when-not process-over?
                  (grid/item {:xs 12 :md 6 :lg 4
                              :style {:flexGrow 1
                                      :minHeight "100px"}}
                    (new-proposal-card {:disabled? (not logged-in?)
                                        :onClick show-new-proposal-dialog}))))

              :hierarchy
              (hierarchy-list/ui-hierarchy-list hierarchy-list {:process-over? process-over?
                                                                :sort-order (keyword selected-sort)})))))

      ; fab
      (when-not process-over?
        (layout/box {:pt 10}                                ; prevent the fab from blocking content below
          (add-proposal-fab {:onClick show-new-proposal-dialog
                             :disabled (not logged-in?)}))))))