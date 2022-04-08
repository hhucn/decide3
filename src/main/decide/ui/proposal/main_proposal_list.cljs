(ns decide.ui.proposal.main-proposal-list
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.algorithms.merge :as mrg]
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
    [decide.routes :as routes]
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
    ["@mui/icons-material/ViewModule" :default ViewModule]
    [taoensso.timbre :as log]
    [decide.ui.proposal.new-proposal :as new-proposal]))

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

(defsc SortSelector [_ {:keys [selected]
                        :or {selected "most-approvals"}} {:keys [set-selected!]}]
  ;; query + initial-state are not used for now.
  {:query [:selected]
   :initial-state {:selected "most-approvals"}}
  (inputs/textfield
    {:label (i18n/trc "Label for sort order selection" "Sort")
     :select true
     :value (or selected "most-approvals")
     :onChange #(set-selected! (evt/target-value %))
     :size :small}
    (navigation/menu-item {:value "new->old"} (i18n/trc "Sort order option" "New → Old"))
    (navigation/menu-item {:value "old->new"} (i18n/trc "Sort order option" "Old → New"))
    (navigation/menu-item {:value "most-approvals"} (i18n/trc "Sort order option" "Approvals ↓"))))

(def ui-sort-selector (comp/computed-factory SortSelector))


(defn toolbar-info-item [{:keys [label]}]
  (dd/typography {:variant :overline} label))

(defn- layout-button [{:keys [key icon label]}]
  (toggle/button {:value key :key key}
    (dd/tooltip {:title (or label "")}
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

(defmutation select-layout [{:keys [layout]}]
  (action [{:keys [app state ref]}]
    (if (#{:favorite :hierarchy} layout)
      (do
        (swap! state update-in ref assoc :ui/layout layout)
        (df/load! app ref MainProposalList
          {:focus
           [::process/slug
            (case layout
              :favorite :>/favorite-list
              :hierarchy :>/hierarchy-list)]
           :marker ::loading-proposals}))
      (log/warn layout " is not a valid layout."))))


(defmutation refresh-process [{slug ::process/slug}]
  (action [{:keys [app]}]
    (df/load! app [::process/slug slug] MainProposalList {:marker ::loading-proposals})))

(defsc ListControlBar [this {::process/keys [slug no-of-proposals no-of-participants]
                             :keys [ui/layout ui/order-by] :as props}]
  {:query [::process/slug
           ::process/no-of-proposals
           ::process/no-of-participants

           :ui/layout
           :ui/order-by

           [df/marker-table ::loading-proposals]]
   :ident ::process/slug
   :initial-state
   (fn [{slug ::process/slug}]
     {::process/slug slug
      :ui/layout :favorite
      :ui/order-by :most-approvals})
   :use-hooks? true}
  (let [large-ui? (breakpoint/>=? "sm")
        loading-proposals? (df/loading? (get props [df/marker-table ::loading-proposals]))]
    (ui-toolbar
      {:start
       [(refresh-button {:onClick #(comp/transact! this [(refresh-process {::process/slug slug})])
                         :loading? loading-proposals?})
        (when no-of-proposals
          (toolbar-info-item {:label (i18n/trf "Proposals {count}" {:count no-of-proposals})}))
        (when no-of-participants
          (toolbar-info-item {:label (i18n/trf "Participants {count}" {:count no-of-participants})}))]
       :end
       [(layout-selector
          {:value layout
           :onChange #(comp/transact! this [(select-layout {:layout %})])
           :layouts (remove nil? [{:key :favorite
                                   :icon ViewModule
                                   :label (i18n/trc "Proposal list layout" "Favourite layout")}
                                  (when large-ui?
                                    {:key :hierarchy
                                     :icon ViewList
                                     :label (i18n/trc "Proposal list layout" "Hierarchy layout")})])})
        (ui-sort-selector {:selected order-by} {:set-selected! #(m/set-value! this :ui/order-by (keyword %))})]})))

(def ui-list-control-bar (comp/computed-factory ListControlBar))


; TODO This component has become way to big.
; TODO Add empty state.
(defsc MainProposalList [this {::process/keys [slug proposals]
                               :keys [>/favorite-list >/hierarchy-list >/list-control-bar ui/layout ui/order-by]
                               :as props}]
  {:ident ::process/slug
   :query [::process/slug

           {:>/list-control-bar (comp/get-query ListControlBar)}

           {::process/proposals (comp/get-query proposal-card/ProposalCard)}
           ::process/end-time

           {:>/favorite-list (comp/get-query favorite-list/FavoriteList)}
           {:>/hierarchy-list (comp/get-query hierarchy-list/HierarchyList)}

           :ui/layout
           :ui/order-by]
   :initial-state
   (fn [{slug ::process/slug}]
     {::process/slug slug
      ::process/proposals []
      :ui/layout :favorite
      :ui/order-by :most-approvals
      :>/list-control-bar (comp/get-initial-state ListControlBar {::process/slug slug})
      :>/favorite-list {::process/slug slug}
      :>/hierarchy-list {::process/slug slug}})
   :use-hooks? true}
  (let [logged-in? (comp/shared this :logged-in?)
        sorted-proposals (hooks/use-memo #(proposal/rank-by sort-by proposals) [sort-by proposals])
        process-running? (process/running? props)
        process-over? (process/over? props)]
    (comp/fragment
      (layout/container
        {:maxWidth :xl,
         :sx {:pb 10}}                                      ; prevent the fab from blocking content below

        ;; Toolbar
        (ui-list-control-bar list-control-bar)

        ; main list
        (error-boundaries/error-boundary
          (case layout
            :hierarchy
            (hierarchy-list/ui-hierarchy-list hierarchy-list {:process-over? process-over?
                                                              :sort-order (keyword order-by)})


            ; :favorite = default
            (if (and (#{:most-approvals} order-by) (not (empty? sorted-proposals)))
              (favorite-list/ui-favorite-list favorite-list)
              (plain-list/plain-list {}
                (let [proposal-cards (mapv
                                       #(proposal-card/ui-proposal-card % {::process/slug slug
                                                                           :process-over? process-over?})
                                       sorted-proposals)]
                  (cond-> proposal-cards

                    process-running?
                    (conj (new-proposal/card {:disabled? (not logged-in?)
                                              :onClick #(comp/transact! this [(new-proposal/show {:slug slug})])})))))))))

      ; fab
      (when process-running?
        (add-proposal-fab {:onClick #(comp/transact! this [(new-proposal/show {:slug slug})])
                           :disabled (not logged-in?)})))))

(def ui-proposal-list-container (comp/factory MainProposalList))


(declare AllProposalsScreen)

(defmutation init-all-proposals-screen [{slug ::process/slug}]
  (action [{:keys [app state]}]
    (when-not (get-in @state [::id slug])
      (mrg/merge-component! app AllProposalsScreen
        {::id slug
         :ui/proposal-list (comp/get-initial-state MainProposalList {::process/slug slug})}))
    (df/load! app [::process/slug slug] ListControlBar)
    (df/load! app [::process/slug slug] MainProposalList
      {:without #{:>/list-control-bar
                  ({:favorite :>/hierarchy-list
                    :hierarchy :>/favorite-list}
                   (get-in @state [::process/slug slug :ui/layout] :favorite))}
       :marker ::loading-proposals})))

(defsc AllProposalsScreen [_ {:keys [ui/proposal-list]}]
  {:ident ::id
   :query [::id {:ui/proposal-list (comp/get-query MainProposalList)}]
   :route-segment (routes/segment ::routes/process-all-proposals)
   :will-enter
   (fn [app {:process/keys [slug]}]
     (let [ident (comp/get-ident AllProposalsScreen {::id slug})]
       (dr/route-deferred ident
         #(comp/transact! app
            [(init-all-proposals-screen {::process/slug slug})
             (dr/target-ready {:target ident})]))))}
  (ui-proposal-list-container proposal-list))
