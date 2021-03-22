(ns decide.ui.process.personal-dashboard
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.algorithms.merge :as mrg]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [material-ui.data-display.list :as list]
    [material-ui.layout :as layout]
    [material-ui.layout.grid :as grid]
    [material-ui.surfaces :as surfaces]
    ["@material-ui/icons/UnfoldMore" :default UnfoldMoreIcon]
    ["@material-ui/icons/UnfoldLess" :default UnfoldLessIcon]))

(defsc ProposalListItem [_ {::proposal/keys [id nice-id title pro-votes]} {:keys [compact?] :or {compact? false}}]
  {:query [::proposal/id ::proposal/title ::proposal/nice-id ::proposal/created ::proposal/pro-votes]
   :ident ::proposal/id}
  (list/item {:href (str "proposal/" id)
              :component :a
              :button true}
    (list/item-icon {} (str "#" nice-id))
    (list/item-text {:primary title
                     :secondary (when-not compact? (i18n/trf "Approvals {pros}" {:pros pro-votes}))})))

(def ui-proposal-list-item (comp/computed-factory ProposalListItem {:keyfn ::proposal/id}))

(defsc PersonalProposalsList [_ {:keys [MY/personal-proposals]}]
  {:query
   [::process/slug
    {:MY/personal-proposals (comp/get-query ProposalListItem)}]
   :ident ::process/slug
   :initial-state
   (fn [{slug ::process/slug}]
     {::process/slug slug
      :MY/personal-proposals []})
   :use-hooks? true}
  (let [[compact? set-compact] (hooks/use-state false)]
    (surfaces/card {}
      (surfaces/card-header
        {:title (i18n/tr "Proposals you approve")
         #_:action #_(inputs/icon-button {:onClick #(set-compact (not compact?))}
                       (if compact?
                         (dom/create-element UnfoldMoreIcon)
                         (dom/create-element UnfoldLessIcon)))})
      (surfaces/card-content {}
        (list/list {}
          (->> personal-proposals
            (proposal/sort-proposals :most-approvals)
            (map #(ui-proposal-list-item % {:compact? compact?}))))))))

(def ui-personal-proposal-list (comp/computed-factory PersonalProposalsList))

(declare PersonalProcessDashboard)

(defn init-dashboard* [state {slug ::process/slug}]
  (mrg/merge-component state PersonalProcessDashboard
    (comp/get-initial-state PersonalProcessDashboard {::process/slug slug})))

(defmutation init-dashboard [{slug ::process/slug}]
  (action [{:keys [app state]}]
    (df/load! app [::process/slug slug] PersonalProposalsList
      {:parallel true
       :marker ::personal-proposals})
    (swap! state init-dashboard* {::process/slug slug})))

(defsc PersonalProcessDashboard [_ {slug ::process/slug
                                    ::keys [personal-proposal-list]}]
  {:query [::process/slug
           {::personal-proposal-list (comp/get-query PersonalProposalsList)}]
   :ident (fn [] [::PersonalProcessDashboard slug])
   :initial-state
   (fn [{slug ::process/slug}]
     {::process/slug slug
      ::personal-proposal-list (comp/get-initial-state PersonalProposalsList {::process/slug slug})})
   :route-segment ["dashboard"]
   :will-enter
   (fn [app {::process/keys [slug]}]
     (let [ident (comp/get-ident PersonalProcessDashboard {::process/slug slug})]
       (dr/route-deferred ident
         #(comp/transact! app
            [(init-dashboard {::process/slug slug})
             (dr/target-ready {:target ident})]))))}
  (layout/container {:maxWidth :xl}
    (layout/box {:m 2}
      (grid/container {:spacing 2}
        (grid/item {:xs 12 :sm 6 :xl 4}
          (ui-personal-proposal-list personal-proposal-list))))))
