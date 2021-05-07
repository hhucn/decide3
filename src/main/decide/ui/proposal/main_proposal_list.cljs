(ns decide.ui.proposal.main-proposal-list
  (:require
    [clojure.string :as str]
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.react.error-boundaries :as error-boundaries]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.utils.time :as time]
    [decide.ui.proposal.card :as proposal-card]
    [decide.utils.breakpoint :as breakpoint]
    [material-ui.data-display :as dd]
    [material-ui.data-display.list :as list]
    [material-ui.inputs :as inputs]
    [material-ui.inputs.form :as form]
    [material-ui.inputs.input :as input]
    [material-ui.lab.toggle-button :as toggle]
    [material-ui.layout :as layout]
    [material-ui.layout.grid :as grid]
    [material-ui.navigation :as navigation]
    [material-ui.surfaces :as surfaces]
    ["@material-ui/icons/Add" :default AddIcon]
    ["@material-ui/icons/ViewList" :default ViewList]
    ["@material-ui/icons/ViewModule" :default ViewModule]
    ["@material-ui/icons/Refresh" :default Refresh]))


(defn add-proposal-fab [props]
  (let [extended? (breakpoint/>=? "sm")]
    (layout/box
      {:position "fixed"
       :bottom   "16px"
       :right    "16px"}
      (inputs/fab
        (merge
          {:aria-label (i18n/tr "New proposal")
           :title (i18n/tr "New proposal")
           :color "secondary"
           :variant (if extended? "extended" "round")}
          props)
        (comp/create-element AddIcon nil nil)
        (when extended?
          (layout/box {:ml 1}
            (i18n/tr "New proposal")))))))

(defn empty-proposal-list-message []
  (layout/box {:p 2 :mx "auto"}
    (dd/typography {:align "center"
                    :color "textSecondary"}
      (i18n/tr "So far there are no arguments"))))

(defn sort-selector [selected set-selected!]
  (form/control {:size :small}
    (input/label {:htmlFor "main-proposal-list-sort"} (i18n/trc "Label for sort order selection" "Sort"))
    (inputs/native-select
      {:value selected
       :onChange (fn [e]
                   (set-selected! (evt/target-value e)))
       :inputProps {:id "main-proposal-list-sort"}}
      (dom/option {:value "new->old"} (i18n/trc "Sort order option" "New → Old"))
      (dom/option {:value "old->new"} (i18n/trc "Sort order option" "Old → New"))
      (dom/option {:value "most-approvals"} (i18n/trc "Sort order option" "Approvals ↓")))))

(def filter-types
  {"merges" "Merges"
   "forks" "Forks"
   "parents" "Parents"})

(defn filter-selector [selected set-selected!]
  (form/control {:size :small :disabled true}
    (input/label {:htmlFor "main-proposal-list-filter"} (i18n/trc "Label for filter selection" "Filter"))
    (inputs/select
      {:multiple true
       :autoWidth true
       :value (to-array selected)
       :style {:minWidth "150px"}
       :onChange (fn [e] (set-selected! (set (evt/target-value e))))
       :inputProps {:id "main-proposal-list-filter"}
       :renderValue (fn [v] (str/join ", " (map filter-types (js->clj v))))}
      (for [[value label] filter-types]
        (navigation/menu-item {:key value :value value}
          (inputs/checkbox {:checked (contains? selected value)})
          (list/item-text {:primary label}))))))

(defn plain-list [{:keys [items card-props]}]
  (vec
    (for [{id ::proposal/id :as proposal} items]
      (grid/item {:xs 12 :md 6 :lg 4 :key id :style {:flexGrow 1}}
        (proposal-card/ui-proposal-card proposal card-props)))))

(defn favorite-list
  "Displays a list of proposal card `items` with the first element prominent at the top."
  [{:keys [items card-props]}]
  (comp/fragment
    (let [best-proposal (first items)]
      (grid/item {:xs 12}
        (layout/container {:maxWidth :lg :disableGutters true}
          (layout/box {:pb 5}
            (dd/typography {:variant :overline} (i18n/tr "This is the best proposal for the moment"))
            (proposal-card/ui-proposal-card
              best-proposal
              (update
                (comp/get-computed best-proposal)
                :card-props
                assoc
                :variant :elevation
                :elevation 12))))))

    (grid/item {:xs 12}
      (dd/divider {})
      (dd/typography {:variant :overline} (i18n/tr "All other proposals")))
    (plain-list {:items (rest items)})))

(defn hierarchy-list
  [{:keys [items card-props]}]
  (grid/container {:spacing 5}
    (vec
      (for [{id ::proposal/id :as proposal} items]
        (grid/container {:xs 12 :key id :style {:flexGrow 1} :item true :spacing 1}
          (layout/box {:clone true}
            (grid/item {:xs 12 :lg 4}
              (proposal-card/ui-proposal-card proposal (assoc card-props :elevation 10))))

          (grid/item {:xs 12 :lg 8}
            (dd/typography {:variant :overline} (i18n/tr "Children"))
            (grid/container {:item true :spacing 1 :direction :row}
              (vec
                (for [proposal (::proposal/children proposal)]
                  (grid/item {:xs 4 :key (::proposal/id proposal)}
                    (proposal-card/ui-proposal-card proposal card-props)))))))))))

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
  (layout/box {:my 1 :clone true}
    (apply surfaces/toolbar {:disableGutters true :variant :dense} children)))

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
      (for [[v icon] buttons]
        (toggle/button {:value v}
          (dom/create-element icon))))))

(defsc MainProposalList [this {::process/keys [slug proposals no-of-contributors end-time no-of-participants]
                               :keys [root/current-session]}
                         {:keys [show-new-proposal-dialog]}]
  {:query [::process/slug
           {::process/proposals (comp/get-query proposal-card/ProposalCard)}
           ::process/no-of-contributors
           ::process/no-of-participants
           ::process/end-time
           [:root/current-session '_]]
   :ident ::process/slug
   :route-segment ["proposals"]
   :will-enter (fn [app {::process/keys [slug]}]
                 (let [ident (comp/get-ident MainProposalList {::process/slug slug})]
                   (dr/route-deferred ident
                     (fn []
                       (if (get-in (app/current-state app) ident)
                         (do
                           (df/load! app ident MainProposalList {:parallel true})
                           (dr/target-ready! app ident))
                         (df/load! app ident MainProposalList
                           {:post-mutation `dr/target-ready
                            :post-mutation-params {:target ident}}))))))
   :use-hooks? true}
  (let [logged-in? (get current-session :session/valid?)
        [selected-sort set-selected-sort!] (hooks/use-state "most-approvals")
        [selected-layout set-selected-layout!] (hooks/use-state :favorite)
        sorted-proposals (hooks/use-memo #(proposal/rank-by selected-sort proposals) [selected-sort proposals])
        process-over? (hooks/use-memo #(boolean (some-> % end-time time/past?)) [end-time])
        >=-sm? (breakpoint/>=? "sm")]
    (comp/fragment
      (layout/container {:maxWidth :xl}

        ; sort / filter toolbar
        (main-list-toolbar {}
          ;; left side
          (grid/container {:spacing 2, :alignItems :center}
            (inputs/button {:onClick #(df/refresh! this)
                            :startIcon (dom/create-element Refresh)}
              (i18n/trc "Reload content" "Refresh"))
            (info-toolbar-item
              {:label (i18n/trf "Proposals: {count}" {:count (count sorted-proposals)})})
            (info-toolbar-item
              {:label (i18n/trf "Participants {count}"
                        {:count (str (max no-of-participants no-of-contributors 0))})}))

          ;; right side
          (grid/container
            {:spacing 2, :alignItems :center, :justify :flex-end}
            (layout-selector
              {:value selected-layout
               :onChange set-selected-layout!}
              (cond-> {:favorite ViewModule}
                >=-sm? (assoc :hierarchy ViewList)))

            (grid/item {} (sort-selector selected-sort set-selected-sort!))))

        ; main list
        (error-boundaries/error-boundary
          (let [computed {::process/slug slug
                          :process-over? process-over?
                          :card-props {:variant (when >=-sm? :outlined)}}
                list-options {:items (mapv #(comp/computed % computed) sorted-proposals)}]
            (case selected-layout
              :favorite
              (grid/container {:spacing (if >=-sm? 2 1)
                               :alignItems "stretch"}
                (if (and (#{"most-approvals"} selected-sort) (not (empty? sorted-proposals)))
                  (favorite-list list-options)
                  (plain-list list-options))
                (when-not process-over?
                  (grid/item {:xs 12 :md 6 :lg 4
                              :style {:flexGrow 1
                                      :minHeight "100px"}}
                    (new-proposal-card {:disabled? (not logged-in?)
                                        :onClick show-new-proposal-dialog}))))

              :hierarchy
              (hierarchy-list list-options)))))

      ; fab
      (when-not process-over?
        (layout/box {:pt 10}                                ; prevent the fab from blocking content below
          (add-proposal-fab {:onClick show-new-proposal-dialog
                             :disabled (not logged-in?)}))))))