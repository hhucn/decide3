(ns decide.ui.proposal.main-proposal-list
  (:require
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
    [mui.data-display :as dd]
    [mui.inputs :as inputs]
    [mui.inputs.toggle-button :as toggle]
    [mui.layout :as layout]
    [mui.layout.grid :as grid]
    [mui.navigation :as navigation]
    [mui.surfaces :as surfaces]
    ["@mui/icons-material/Add" :default AddIcon]
    ["@mui/icons-material/Refresh" :default Refresh]
    ["@mui/icons-material/ViewList" :default ViewList]
    ["@mui/icons-material/ViewModule" :default ViewModule]))

(declare MainProposalList)

(defn add-proposal-fab [props]
  (inputs/fab
    (merge
      {:aria-label (i18n/tr "New proposal")
       :title (i18n/tr "New proposal")
       :color :secondary
       :variant :extended
       :sx {:position :fixed
            :bottom "16px"
            :right "16px"}}
      props)
    (dom/create-element AddIcon #js {:sx #js{:mr 1}})
    (i18n/tr "New proposal")))

(defsc SortSelector [_ {:keys [selected]} {:keys [set-selected!]}]
  ;; query + initial-state are not used for now.
  {:query [:selected]
   :initial-state {:selected "most-approvals"}}
  (inputs/textfield
    {:label (i18n/trc "Label for sort order selection" "Sort")
     :select true
     :value selected
     :onChange #(set-selected! (evt/target-value %))
     :size :small}
    (navigation/menu-item {:value "new->old"} (i18n/trc "Sort order option" "New → Old"))
    (navigation/menu-item {:value "old->new"} (i18n/trc "Sort order option" "Old → New"))
    (navigation/menu-item {:value "most-approvals"} (i18n/trc "Sort order option" "Approvals ↓"))))

(def ui-sort-selector (comp/computed-factory SortSelector))

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

(defn info-toolbar-item [{:keys [label]}]
  (dd/typography {:variant :overline} label))

(defn- layout-button [{:keys [key icon label]}]
  (dd/tooltip {:title (or label "")}
    (toggle/button {:value key :key key}
      (dom/create-element icon))))

(defn layout-selector [{:keys [value onChange layouts]}]
  (when (< 1 (count layouts))
    (toggle/button-group
      {:exclusive true
       :size :small
       :value value
       :onChange (fn [_e new-layout] (some-> new-layout keyword onChange))}
      (mapv layout-button layouts))))

(defn refresh-button [{:keys [onClick loading?] :or {loading? false}}]
  (inputs/loading-button
    {:onClick onClick
     :loading loading?
     :variant :outlined
     :loadingPosition :start
     :startIcon (dom/create-element Refresh)}
    (i18n/trc "Reload content" "Refresh")))

(defsc Toolbar [_ {:keys [start end]}]
  (let [container-props {:item true, :xs :auto, :spacing 2, :alignItems :center}]
    (surfaces/toolbar {:disableGutters true, :sx {:py 1}}
      (grid/container {:spacing 1}
        ;; left side
        (apply grid/container container-props
          (mapv #(grid/item {} %) start))
        ;; right side
        (apply grid/container (assoc container-props :ml :auto)
          (mapv #(grid/item {} %) end))))))

(def ui-toolbar (comp/factory Toolbar))


(defmutation enter-proposal-list [params]
  (action [{:keys [app state ref]}]
    (if (get-in @state ref)                                 ; there was 'a' process loaded
      (do                                                   ;; target ready and refresh
        (dr/target-ready! app ref)
        (df/load! app ref MainProposalList {:parallel true :marker ::loading-proposals}))
      (do                                                   ;; load in two steps
        (df/load! app ref MainProposalList
          {:post-mutation `dr/target-ready
           :post-mutation-params {:target ref}
           :without #{::process/proposals :>/favorite-list :>/hierarchy-list}})
        (df/load! app ref MainProposalList
          {:focus [::process/slug ::process/proposals :>/favorite-list :>/hierarchy-list]
           :marker ::loading-proposals})))))

; TODO This component has become way to big.
; TODO Add empty state.
(defsc MainProposalList [this {::process/keys [slug proposals no-of-participants no-of-proposals]
                               :keys [>/favorite-list >/hierarchy-list ui/layout ui/sort-by]
                               :as props
                               :or {no-of-participants 0, no-of-proposals 0}}
                         {:keys [show-new-proposal-dialog]}]
  {:ident ::process/slug
   :query [::process/slug
           {::process/proposals (comp/get-query proposal-card/ProposalCard)}
           ::process/no-of-proposals
           ::process/no-of-participants
           ::process/end-time

           {:>/favorite-list (comp/get-query favorite-list/FavoriteList)}
           {:>/hierarchy-list (comp/get-query hierarchy-list/HierarchyList)}

           :ui/layout
           :ui/sort-by

           [df/marker-table ::loading-proposals]]
   :pre-merge (fn [{:keys [current-normalized data-tree]}]
                (merge
                  {:ui/sort-by :most-approvals
                   :ui/layout :favorite}
                  current-normalized
                  data-tree))
   :route-segment ["proposals"]
   :will-enter (fn [app {::process/keys [slug]}]
                 (let [ident (comp/get-ident MainProposalList {::process/slug slug})]
                   (dr/route-deferred ident
                     #(comp/transact! app [(enter-proposal-list {})] {:ref ident}))))
   :use-hooks? true}
  (let [logged-in? (comp/shared this :logged-in?)
        loading-proposals? (df/loading? (get props [df/marker-table ::loading-proposals]))
        sorted-proposals (hooks/use-memo #(proposal/rank-by sort-by proposals) [sort-by proposals])
        process-running? (process/running? props)
        process-over? (process/over? props)
        large-ui? (breakpoint/>=? "sm")]
    (comp/fragment
      (layout/container {:maxWidth :xl}

        (ui-toolbar
          {:start
           [(refresh-button {:onClick #(df/refresh! this {:marker ::loading-proposals})
                             :loading? loading-proposals?})
            (dd/typography {:variant :overline} (i18n/trf "Proposals {count}" {:count no-of-proposals}))
            (dd/typography {:variant :overline} (i18n/trf "Participants {count}" {:count no-of-participants}))]
           :end
           [(layout-selector
              {:value layout, :onChange #(m/set-value! this :ui/layout %)
               :layouts (remove nil? [{:key :favorite
                                       :icon ViewModule
                                       :label (i18n/trc "Proposal list layout" "Favourite layout")}
                                      (when large-ui?
                                        {:key :hierarchy
                                         :icon ViewList
                                         :label (i18n/trc "Proposal list layout" "Hierarchy layout")})])})
            (ui-sort-selector {:selected sort-by} {:set-selected! #(m/set-value! this :ui/sort-by (keyword %))})]})

        ; main list
        (error-boundaries/error-boundary
          (let [context {::process/slug slug
                         :process-over? process-over?
                         :card-props {:variant (when large-ui? :outlined)}}
                list-options (merge
                               {:items (mapv #(comp/computed % context) sorted-proposals)}
                               context)]
            (case layout
              :favorite
              (grid/container {:spacing (if large-ui? 2 1)
                               :alignItems "stretch"
                               :style {:position "relative"}}
                (if (and (#{:most-approvals} sort-by) (not (empty? sorted-proposals)))
                  (favorite-list/ui-favorite-list favorite-list)
                  (plain-list/plain-list list-options))
                (when process-running?
                  (grid/item {:xs 12 :md 6 :lg 4
                              :style {:flexGrow 1
                                      :minHeight "100px"}}
                    (new-proposal-card {:disabled? (not logged-in?)
                                        :onClick show-new-proposal-dialog}))))

              :hierarchy
              (hierarchy-list/ui-hierarchy-list hierarchy-list {:process-over? process-over?
                                                                :sort-order (keyword sort-by)})))))

      ; fab
      (when process-running?
        (layout/box {:pt 10}                                ; prevent the fab from blocking content below
          (add-proposal-fab {:onClick show-new-proposal-dialog
                             :disabled (not logged-in?)}))))))