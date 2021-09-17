(ns decide.ui.process.home
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [decide.models.opinion.api :as opinion.api]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.ui.process.ballot :as ballot]
    [decide.ui.proposal.card :as proposal-card]
    [mui.data-display :as dd]
    [mui.data-display.list :as list]
    [mui.inputs :as inputs]
    [mui.layout :as layout]
    [mui.layout.grid :as grid]
    [mui.surfaces :as surfaces]))

(defsc TopEntry [this {::proposal/keys [id title pro-votes my-opinion-value]
                       :keys [root/current-session]}]
  {:query [::proposal/id ::proposal/title ::proposal/pro-votes ::proposal/my-opinion-value
           [:root/current-session '_]]
   :ident ::proposal/id}
  (let [approved? (pos? my-opinion-value)]
    (list/item
      {:button true
       :component :a
       :href (str "proposal/" id)}
      (list/item-text {:primary title
                       :secondary (i18n/trf "Approvals: {pros}" {:pros pro-votes})} title)
      (when (get current-session :session/valid?)
        (list/item-secondary-action {}
          (if (pos? my-opinion-value)
            (layout/box {:color "success.main"} (dd/typography {:color :inherit} (i18n/tr "Approved")))
            (inputs/button
              {:color :primary
               :onClick #(comp/transact! this [(opinion.api/add {::proposal/id id
                                                                 :opinion (if approved? 0 1)})])}

              (i18n/tr "Approve"))))))))

(def ui-top-entry (comp/factory TopEntry {:keyfn ::proposal/id}))

(defn section-paper [props & children]
  (apply surfaces/paper {:variant :outlined, :sx {:p 2}}
    children))

(defn top-proposals [proposals]
  (first (partition-by ::proposal/pro-votes (proposal/rank proposals))))

(defsc ProcessHome [_this {::process/keys [slug description proposals winner]
                           :keys [>/ballot] :as process}]
  {:query [::process/slug ::process/description
           {::process/proposals (comp/get-query TopEntry)}
           ::process/end-time
           {:>/ballot (comp/get-query ballot/Ballot)}
           {::process/winner (comp/get-query proposal-card/ProposalCard)}]
   :ident ::process/slug}
  (let [process-over? (process/over? process)]
    (layout/container {:maxWidth :lg, :component :main, :sx {:pt 2}}
      ;; description section
      (grid/container {:spacing 2}
        (when (and process-over? winner)
          (grid/item {:xs 12}
            (section-paper {}
              (dd/typography {:component :h2 :variant :h4}
                (i18n/tr "Winner"))
              (layout/box {:m 3}
                (proposal-card/ui-proposal-card winner
                  {::process/slug slug
                   :process-over? process-over?
                   :max-height nil
                   :card-props {:raised true
                                :color :primary}})))))

        (grid/item {:xs 12}
          (section-paper {}
            (dd/typography {:component :h2 :variant :h4 :paragraph true}
              (i18n/trc "Description of a process" "Description"))
            (dd/typography {:variant :body1 :style {:whiteSpace "pre-line"}}
              description)))


        (let [top-proposals (top-proposals proposals)]
          (when (or (not process-over?) #_(< 1 (count top-proposals)))
            (when (pos? (count top-proposals))
              (grid/item {:xs 12}
                (section-paper {}
                  (dd/typography {:component :h2 :variant :h4}
                    (i18n/trf
                      "{numProposals, plural, =0 {There are no proposals!} =1 {The current best proposal} other {The current best proposals}}"
                      {:numProposals (count top-proposals)}))
                  (list/list {}
                    (map ui-top-entry top-proposals)))))))

        (when (and (not process-over?) (seq (::process/proposals ballot)))
          (grid/item {:xs 12}
            (section-paper {:pb 0}
              (ballot/header)
              (ballot/ui-ballot ballot))))))))

(def ui-process-home (comp/computed-factory ProcessHome))


(defsc ProcessOverviewScreen [_ {:keys [ui/current-process]}]
  {:query [{[:ui/current-process '_] (comp/get-query ProcessHome)}]
   :ident (fn [] [:SCREEN ::ProcessOverviewScreen])
   :route-segment ["home"]
   :initial-state {:ui/current-process {}}
   :will-enter
   (fn will-enter-ProcessOverviewScreen [app {::process/keys [slug]}]
     (let [ident (comp/get-ident ProcessOverviewScreen {})]
       (dr/route-deferred ident
         (fn []
           (df/load! app [::process/slug slug] ProcessHome)
           (dr/target-ready! app ident)))))}
  (layout/box {:pb 5}
    (ui-process-home current-process)))
