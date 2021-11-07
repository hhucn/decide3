(ns decide.ui.process.personal-dashboard
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.algorithms.merge :as mrg]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [decide.models.opinion.api :as opinion.api]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.routes :as routes]
    [mui.data-display :as dd]
    [mui.data-display.list :as list]
    [mui.inputs :as inputs]
    [mui.layout :as layout]
    [mui.layout.grid :as grid]
    [mui.surfaces :as surfaces]
    [mui.surfaces.card :as card]
    ["@mui/icons-material/UnfoldMore" :default UnfoldMoreIcon]
    ["@mui/icons-material/UnfoldLess" :default UnfoldLessIcon]
    ["@mui/icons-material/ThumbUpAltTwoTone" :default ThumbUpAltTwoTone]))

(defsc ProposalListItem [this {::proposal/keys [id nice-id title pro-votes my-opinion-value]} {:keys [compact?] :or {compact? false}}]
  {:query [::proposal/id ::proposal/title ::proposal/nice-id ::proposal/created ::proposal/pro-votes ::proposal/my-opinion-value]
   :ident ::proposal/id}
  (list/item {:href (str "proposal/" id)
              :component :a
              :button true}
    (list/item-icon {} (str "#" nice-id))
    (list/item-text
      {:primary title
       :secondary (when-not compact? (i18n/trf "Approvals {pros}" {:pros pro-votes}))})
    (list/item-secondary-action {}
      (let [approved? (pos? my-opinion-value)]
        (inputs/icon-button
          {:aria-label
           (if approved?
             (i18n/trc "Proposal has been approved" "Approved")
             (i18n/trc "Approve a proposal" "Approve"))
           :color (if approved? "primary" "default")
           :onClick #(comp/transact! this [(opinion.api/add {::proposal/id id
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
    (card/card {}
      (card/header
        {:title (i18n/tr "Proposals you approve")
         #_:action #_(inputs/icon-button {:onClick #(set-compact (not compact?))}
                       (if compact?
                         (dom/create-element UnfoldMoreIcon)
                         (dom/create-element UnfoldLessIcon)))})
      (card/content {}
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
      (card/card {}
        (card/header
          {:title (i18n/tr "Proposals you might be interested in")
           :subheader (i18n/tr "These are proposals derived from proposals you already approved.")
           #_:action #_(inputs/icon-button {:onClick #(set-compact (not compact?))}
                         (if compact?
                           (dom/create-element UnfoldMoreIcon)
                           (dom/create-element UnfoldLessIcon)))})
        (card/content {}
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
   :route-segment (routes/segment ::routes/process-dashboard)
   :will-enter
   (fn [app {:process/keys [slug]}]
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
