(ns decide.ui.process.home
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [material-ui.data-display :as dd]
    [material-ui.data-display.list :as list]
    [material-ui.inputs :as inputs]
    [material-ui.layout :as layout]
    [material-ui.layout.grid :as grid]
    [material-ui.surfaces :as surfaces]))

(defsc TopEntry [_this {::proposal/keys [id title pro-votes my-opinion]
                        :keys [root/current-session] :as props}]
  {:query [::proposal/id ::proposal/title ::proposal/pro-votes ::proposal/my-opinion
           [:root/current-session '_]]
   :ident ::proposal/id}
  (list/item
    {:button true
     :component :a
     :href (str "proposal/" id)}
    (list/item-text {:primary title
                     :secondary (str "Zustimmungen: " pro-votes)} title)
    (when (get current-session :session/valid?)
      (list/item-secondary-action {}
        (if (pos? my-opinion)
          (layout/box {:color "success.main"} (dd/typography {:color :inherit} "Zugestimmt"))
          (inputs/button {:color :primary}

            "Zustimmen"))))))

(def ui-top-entry (comp/factory TopEntry {:keyfn ::proposal/id}))

(defn section-paper [props & children]
  (layout/box (merge {:clone true :p 2} props)
    (apply surfaces/paper {:variant :outlined} children)))

(defn top-proposals [proposals]
  (first (partition-by ::proposal/pro-votes (sort-by ::proposal/pro-votes > proposals))))

(defn sort-by-votes [proposals]
  (sort-by ::proposal/pro-votes > proposals))

(declare ui-experimental-ballot-entry)

(defsc BallotEntry [_ {::proposal/keys [title pro-votes parents children my-opinion]}]
  {:query [::proposal/id
           ::proposal/title
           ::proposal/pro-votes
           {::proposal/parents 1}
           {::proposal/children 1}
           ::proposal/my-opinion]
   :ident ::proposal/id}
  (list/item {}
    (list/item-icon {}
      (inputs/checkbox
        {:edge :start
         :checked (pos? my-opinion)}))

    (list/item-text {:secondary (str "Zustimmungen: " pro-votes)}
      (if-not (and (empty? parents) (empty? children))

        ;; expandable content
        (layout/box {:component :details}
          (dom/summary {} title)
          (layout/box {:borderLeft 1}
            (when-not (empty? children)
              (list/list
                {:subheader (list/subheader {} "Children")
                 :dense true}
                (map ui-experimental-ballot-entry (sort-by-votes children))))
            (when-not (empty? parents)
              (list/list
                {:subheader (list/subheader {} "Parents")
                 :dense true}
                (map ui-experimental-ballot-entry (sort-by-votes parents))))))

        title))))

(def ui-experimental-ballot-entry (comp/computed-factory BallotEntry {:keyfn ::proposal/id}))

(defsc Ballot [_ {::process/keys [proposals]}]
  {:query [::process/slug
           {::process/proposals (comp/get-query BallotEntry)}]
   :ident ::process/slug}
  (section-paper {:pb 0 :borderColor "warning.main"}        ; TODO remove warning color
    (dd/typography {:component :h2 :variant "h5"} "Stimmzettel"
      (let [sorted-proposals (sort-by-votes proposals)]
        (list/list {}
          (map ui-experimental-ballot-entry sorted-proposals))))))

(def ui-experimental-ballot (comp/computed-factory Ballot))

(defsc ProcessHome [_this {::process/keys [description proposals]
                           :keys [>/experimental-ballots]}]
  {:query [::process/slug ::process/description
           {::process/proposals (comp/get-query TopEntry)}
           {:>/experimental-ballots (comp/get-query Ballot)}]
   :ident ::process/slug}
  (layout/box {:clone true :pt 2}
    (layout/container {:maxWidth :lg :component :main}
      ;; description section
      (grid/container {:spacing 2}
        (grid/item {:xs 12}
          (section-paper {}
            (dd/typography {:component :h2 :variant "h4" :paragraph true}
              "Beschreibung")
            (dd/typography {:variant "body1"}
              description)))


        (let [top-proposals (top-proposals proposals)]
          (when-not (zero? (count top-proposals))
            (grid/item {:xs 12}
              (section-paper {:pb 0}
                (dd/typography {:component :h2 :variant "h5"}
                  (case (count top-proposals)
                    0 "Es gibt keine Vorschläge!"
                    1 "Der aktuell beste Vorschlag"
                    "Die aktuell besten Vorschläge"))
                (list/list {}
                  (map ui-top-entry top-proposals))))))

        (grid/item {:xs 12}
          (ui-experimental-ballot experimental-ballots))))))

(def ui-process-home (comp/computed-factory ProcessHome))

(defmutation init-overview-screen [{:keys [slug]}]
  (action [{:keys [app ref]}]
    (let [process-ident [::process/slug slug]]

      (df/load! app process-ident ProcessHome
        {:target (conj ref :process-home)
         :post-mutation `dr/target-ready
         :post-mutation-params {:target ref}}))))

(defsc ProcessOverviewScreen [_ {:keys [process-home] :as props}]
  {:query [{:process-home (comp/get-query ProcessHome)}]
   :ident (fn [] [:SCREEN ::ProcessOverviewScreen])
   :route-segment ["home"]
   :will-enter
   (fn will-enter-ProcessOverviewScreen [app {::process/keys [slug]}]
     (let [ident (comp/get-ident ProcessOverviewScreen {:process-home {::process/slug slug}})]
       (dr/route-deferred ident
         #(comp/transact! app [(init-overview-screen {:slug slug})] {:ref ident}))))}
  (ui-process-home process-home))
