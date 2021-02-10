(ns decide.ui.process.home
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [material-ui.data-display :as dd]
    [material-ui.data-display.list :as list]
    [material-ui.layout :as layout]
    [material-ui.surfaces :as surfaces]
    [material-ui.inputs :as inputs]
    [taoensso.timbre :as log]
    [material-ui.layout.grid :as grid]))

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

(defsc ProcessHome [_this {::process/keys [description proposals]}]
  {:query [::process/slug ::process/description
           {::process/proposals (comp/get-query TopEntry)}]
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
                  (map ui-top-entry top-proposals))))))))))

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
