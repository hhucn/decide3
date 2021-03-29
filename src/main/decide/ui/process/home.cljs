(ns decide.ui.process.home
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [decide.models.opinion :as opinion]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.ui.proposal.card :as proposal-card]
    [material-ui.data-display :as dd]
    [material-ui.data-display.list :as list]
    [material-ui.inputs :as inputs]
    [material-ui.layout :as layout]
    [material-ui.layout.grid :as grid]
    [material-ui.surfaces :as surfaces]))

(defsc TopEntry [this {::proposal/keys [id title pro-votes my-opinion]
                       :keys [root/current-session]}]
  {:query [::proposal/id ::proposal/title ::proposal/pro-votes ::proposal/my-opinion
           [:root/current-session '_]]
   :ident ::proposal/id}
  (let [approved? (pos? my-opinion)]
    (list/item
      {:button true
       :component :a
       :href (str "proposal/" id)}
      (list/item-text {:primary title
                       :secondary (i18n/trf "Approvals: {pros}" {:pros pro-votes})} title)
      (when (get current-session :session/valid?)
        (list/item-secondary-action {}
          (if (pos? my-opinion)
            (layout/box {:color "success.main"} (dd/typography {:color :inherit} (i18n/tr "Approved")))
            (inputs/button
              {:color :primary
               :onClick #(comp/transact! this [(opinion/add {::proposal/id id
                                                             :opinion (if approved? 0 1)})])}

              (i18n/tr "Approve"))))))))

(def ui-top-entry (comp/factory TopEntry {:keyfn ::proposal/id}))

(defn section-paper [props & children]
  (layout/box (merge {:clone true :p 2} props)
    (apply surfaces/paper {:variant :outlined} children)))

(defn top-proposals [proposals]
  (first (partition-by ::proposal/pro-votes (proposal/rank proposals))))

(declare ui-experimental-ballot-entry)

(defsc BallotEntry [this {::proposal/keys [id title pro-votes my-opinion]}]
  {:query [::proposal/id
           ::proposal/title
           ::proposal/pro-votes
           ::proposal/my-opinion]
   :ident ::proposal/id}
  (let [approved? (pos? my-opinion)]
    (list/item {}
      (list/item-icon {}
        (inputs/checkbox
          {:edge :start
           :checked approved?
           :onClick #(comp/transact! this [(opinion/add {::proposal/id id
                                                         :opinion (if approved? 0 1)})])}))

      (list/item-text
        {:primary title
         :secondary (i18n/trf "Approvals: {pros}" {:pros pro-votes})}))))

(def ui-experimental-ballot-entry (comp/computed-factory BallotEntry {:keyfn ::proposal/id}))

(defsc Ballot [_ {::process/keys [proposals]}]
  {:query [::process/slug
           {::process/proposals (comp/get-query BallotEntry)}]
   :ident ::process/slug}
  (section-paper {:pb 0}
    (dd/typography {:component :h2 :variant "h5"} (i18n/tr "Your approvals")
      (list/list {:dense true}
        (->> proposals
          proposal/rank
          (map ui-experimental-ballot-entry))))))

(def ui-experimental-ballot (comp/computed-factory Ballot))

(defsc ProcessHome [_this {::process/keys [slug description proposals winner]
                           :keys [>/experimental-ballots] :as process}]
  {:query [::process/slug ::process/description
           {::process/proposals (comp/get-query TopEntry)}
           ::process/end-time
           {:>/experimental-ballots (comp/get-query Ballot)}
           {::process/winner (comp/get-query proposal-card/ProposalCard)}]
   :ident ::process/slug}
  (let [process-over? (process/over? process)]
    (layout/box {:clone true :pt 2}
      (layout/container {:maxWidth :lg :component :main}
        ;; description section
        (grid/container {:spacing 2}
          (when process-over?
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
                        "{numProposals, plural,
                        =0 {There are no proposals!}
                        =1 {The current best proposal}
                        other {The current best proposals}}"
                        {:numProposals (count top-proposals)}))
                    (list/list {}
                      (map ui-top-entry top-proposals)))))))

          (when-not process-over?
            (grid/item {:xs 12}
              (ui-experimental-ballot experimental-ballots))))))))

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
  (ui-process-home current-process))
