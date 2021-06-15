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
    ["@material-ui/icons/UnfoldLess" :default UnfoldLessIcon]
    ["@material-ui/icons/ThumbUpAltTwoTone" :default ThumbUpAltTwoTone]
    [taoensso.timbre :as log]
    [material-ui.inputs :as inputs]
    [decide.models.opinion :as opinion]
    [com.fulcrologic.fulcro.dom :as dom]
    [material-ui.data-display :as dd]))

(defsc ProposalListItem [this {::proposal/keys [id nice-id title pro-votes my-opinion]} {:keys [compact?] :or {compact? false}}]
  {:query [::proposal/id ::proposal/title ::proposal/nice-id ::proposal/created ::proposal/pro-votes ::proposal/my-opinion]
   :ident ::proposal/id}
  (list/item {:href (str "proposal/" id)
              :component :a
              :button true}
    (list/item-icon {} (str "#" nice-id))
    (list/item-text
      {:primary title
       :secondary (when-not compact? (i18n/trf "Approvals {pros}" {:pros pro-votes}))})
    (list/item-secondary-action {}
      (let [approved? (pos? my-opinion)]
        (inputs/icon-button
          {:aria-label
           (if approved?
             (i18n/trc "Proposal has been approved" "Approved")
             (i18n/trc "Approve a proposal" "Approve"))
           :color (if approved? "primary" "default")
           :onClick #(comp/transact! this [(opinion/add {::proposal/id id
                                                         :opinion (if approved? 0 1)})])}
          (dom/create-element ThumbUpAltTwoTone #js {"fontSize" "small"}))))))


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
        (if (seq personal-proposals)
          (list/list {}
            (->> personal-proposals
              proposal/rank
              (map #(ui-proposal-list-item % {:compact? compact?}))))
          (dd/typography {:variant :body2
                          :color :textSecondary}
            (i18n/tr "You don't approve any proposals yet.")))))))

(def ui-personal-proposal-list (comp/computed-factory PersonalProposalsList))

(defsc PersonalRecommendationsList [_ {:keys [MY/proposal-recommendations] :as props}]
  {:query
   [::process/slug
    {:MY/proposal-recommendations (comp/get-query ProposalListItem)}]
   :ident ::process/slug
   :initial-state
   (fn [{slug ::process/slug}]
     {::process/slug slug
      :MY/proposal-recommendations []})
   :use-hooks? true}
  (let [[compact? set-compact] (hooks/use-state false)]
    (when-not (empty? proposal-recommendations)
      (surfaces/card {}
        (surfaces/card-header
          {:title (i18n/tr "Proposals you might be interested in")
           :subheader (i18n/tr "These are proposals derived from proposals you already approved.")
           #_:action #_(inputs/icon-button {:onClick #(set-compact (not compact?))}
                         (if compact?
                           (dom/create-element UnfoldMoreIcon)
                           (dom/create-element UnfoldLessIcon)))})
        (surfaces/card-content {}
          (list/list {}
            (->> proposal-recommendations
              proposal/rank
              (map #(ui-proposal-list-item % {:compact? compact?})))))))))

(def ui-personal-recommendations-list (comp/computed-factory PersonalRecommendationsList))

(declare PersonalProcessDashboard)

(defn init-dashboard* [state {slug ::process/slug}]
  (mrg/merge-component state PersonalProcessDashboard
    (comp/get-initial-state PersonalProcessDashboard {::process/slug slug})))

(defmutation init-dashboard [{slug ::process/slug :as params}]
  (action [{:keys [app state]}]
    (log/spy :warn params)
    (df/load! app [::process/slug slug] PersonalProposalsList
      {:parallel true
       :marker ::personal-proposals})
    (df/load! app [::process/slug slug] PersonalRecommendationsList
      {:parallel true
       :marker ::personal-recommendations})
    (swap! state init-dashboard* {::process/slug slug})))

(defsc PersonalProcessDashboard [_ {slug ::process/slug
                                    ::keys [personal-proposal-list personal-recommendations-list]}]
  {:query [::process/slug
           {::personal-proposal-list (comp/get-query PersonalProposalsList)}
           {::personal-recommendations-list (comp/get-query PersonalRecommendationsList)}]
   :ident (fn [] [::PersonalProcessDashboard slug])
   :initial-state
   (fn [{slug ::process/slug}]
     (when slug
       {::process/slug slug
        ::personal-proposal-list (comp/get-initial-state PersonalProposalsList {::process/slug slug})
        ::personal-recommendations-list (comp/get-initial-state PersonalRecommendationsList {::process/slug slug})}))
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
        (grid/item {:xs 12}
          (surfaces/paper {}
            (layout/box {:p 2}
              (dd/typography {:variant :body1}
                (i18n/tr "Here you can find a personal overview and recommendations for this process.")))))
        (grid/item {:xs 12 :lg 6 :xl 4}
          (ui-personal-proposal-list personal-proposal-list))
        (grid/item {:xs 12 :lg 6 :xl 4}
          (ui-personal-recommendations-list personal-recommendations-list))))))
