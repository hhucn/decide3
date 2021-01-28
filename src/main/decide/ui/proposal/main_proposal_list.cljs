(ns decide.ui.proposal.main-proposal-list
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.ui.proposal.card :as proposal-card]
    [decide.utils.breakpoint :as breakpoint]
    [material-ui.data-display :as dd]
    [material-ui.inputs :as inputs]
    [material-ui.inputs.form :as form]
    [material-ui.inputs.input :as input]
    [material-ui.layout :as layout]
    [material-ui.layout.grid :as grid]
    [material-ui.surfaces :as surfaces]
    ["@material-ui/icons/Add" :default AddIcon]
    [material-ui.navigation :as navigation]
    [material-ui.data-display.list :as list]
    [clojure.string :as str]))


(defn add-proposal-fab [props]
  (let [extended? (breakpoint/>=? "sm")]
    (layout/box
      {:position "fixed"
       :bottom   "16px"
       :right    "16px"}
      (inputs/fab
        (merge
          {:aria-label "Neuer Vorschlag"
           :title      "Neuer Vorschlag"
           :color      "secondary"
           :variant    (if extended? "extended" "round")}
          props)
        (comp/create-element AddIcon nil nil)
        (when extended?
          (layout/box {:ml 1}
            "Neuer Vorschlag"))))))

(defn empty-proposal-list-message []
  (layout/box {:p 2 :mx "auto"}
    (dd/typography {:align "center"
                    :color "textSecondary"}
      "Bisher gibt es keine Vorschläge.")))

(defmulti sort-proposals (fn [sort-order _] (keyword sort-order)))

(defmethod sort-proposals :old->new [_ proposals]
  (sort-by ::proposal/nice-id < proposals))

(defmethod sort-proposals :new->old [_ proposals]
  (sort-by ::proposal/nice-id > proposals))

(defmethod sort-proposals :most-approvals [_ proposals]
  (sort-by ::proposal/pro-votes > proposals))

(defmethod sort-proposals :default [_ proposals]
  (sort-proposals "old->new" proposals))

(defn sort-selector [selected set-selected!]
  (form/control {:size :small}
    (input/label {:htmlFor "main-proposal-list-sort"} "Sortierung")
    (inputs/native-select
      {:value selected
       :onChange (fn [e]
                   (set-selected! (evt/target-value e)))
       :inputProps {:id "main-proposal-list-sort"}}
      (dom/option {:value "new->old"} "Neu → Alt")
      (dom/option {:value "old->new"} "Alt → Neu")
      (dom/option {:value "most-approvals"} "Zustimmungen ↓"))))

(def filter-types
  {"merges" "Merges"
   "forks" "Forks"
   "parents" "Parents"})

(defn filter-selector [selected set-selected!]
  (form/control {:size :small :disabled true}
    (input/label {:htmlFor "main-proposal-list-filter"} "Filter")
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

(defsc MainProposalList [_ {::process/keys [slug proposals no-of-contributors] :keys [root/current-session]} {:keys [show-new-proposal-dialog]}]
  {:query [::process/slug
           {::process/proposals (comp/get-query proposal-card/ProposalCard)}
           ::process/no-of-contributors
           [:root/current-session '_]]
   :ident ::process/slug
   :route-segment ["proposals"]
   :will-enter (fn [app {::process/keys [slug]}]
                 (let [ident (comp/get-ident MainProposalList {::process/slug slug})]
                   (dr/route-deferred ident
                     #(df/load! app ident MainProposalList
                        {:post-mutation `dr/target-ready
                         :post-mutation-params {:target ident}}))))
   :use-hooks? true}
  (let [logged-in? (get current-session :session/valid?)
        [selected-sort set-selected-sort!] (hooks/use-state "new->old")
        [selected-filters set-selected-filters!] (hooks/use-state #{})
        sorted-proposals (sort-proposals selected-sort proposals)]
    (comp/fragment
      (layout/container {:maxWidth :xl}
        (layout/box {:my 1 :clone true}
          (surfaces/toolbar {:disableGutters true :variant :dense}
            (grid/container {:spacing 2}
              (grid/item {}
                (dd/typography {:variant :overline}
                  "Vorschläge: "
                  (count sorted-proposals)))
              (grid/item {}
                (dd/typography {:variant :overline}
                  "Teilnehmer: " (str no-of-contributors))))
            (grid/container {:item true :spacing 2
                             :justify "flex-end"}
              (grid/item {} (filter-selector selected-filters set-selected-filters!))
              (grid/item {} (sort-selector selected-sort set-selected-sort!)))))
        (grid/container {:spacing 2 :alignItems "stretch"}
          (for [{id ::proposal/id :as proposal} sorted-proposals]
            (grid/item {:xs 12 :md 6 :lg 4 :xl 3 :key id :style {:flexGrow 1}}
              (proposal-card/ui-proposal-card proposal {::process/slug slug})))
          (grid/item {:xs 12 :md 6 :lg 4 :xl 3 :style {:flexGrow 1
                                                       :minHeight "100px"}}
            (inputs/button {:style {:height "100%"
                                    :borderStyle "dashed"}
                            :fullWidth true
                            :size :large
                            :disabled (not logged-in?)
                            :variant :outlined
                            :onClick show-new-proposal-dialog}
              (layout/box {:color (when-not logged-in? "text.disabled") :mr 1 :component AddIcon})
              (if logged-in?
                "Neuer Vorschlag"
                "Einloggen um einen neuen Vorschlag hinzuzufügen")))))
      (layout/box {:pt 10}                                  ; prevent the fab from blocking content below
        (add-proposal-fab {:onClick show-new-proposal-dialog
                           :disabled (not logged-in?)})))))