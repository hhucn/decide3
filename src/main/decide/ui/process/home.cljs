(ns decide.ui.process.home
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [material-ui.data-display :as dd]
    [material-ui.data-display.list :as list]
    [material-ui.inputs :as inputs]
    [material-ui.layout :as layout]))


(defsc TopEntry [_this {::proposal/keys [id title]}]
  {:query [::proposal/id ::proposal/title ::proposal/pro-votes]
   :ident ::proposal/id}
  (list/item {} (str title)))
(def ui-top-entry (comp/factory TopEntry {:keyfn ::proposal/id}))

(defsc ProcessHome [_this {::process/keys [slug description proposals]}]
  {:query [::process/slug ::process/description
           {::process/proposals (comp/get-query TopEntry)}]
   :ident ::process/slug
   :route-segment ["home"]
   :will-enter
   (fn will-enter-process-home [app {::process/keys [slug]}]
     (let [ident (comp/get-ident ProcessHome {::process/slug slug})]
       (dr/route-deferred ident
         (fn []
           (df/load! app ident ProcessHome
             {:post-mutation `dr/target-ready
              :post-mutation-params {:target ident}})))))}
  (layout/container {:maxWidth :xl}
    (dd/typography {:paragraph true} description)
    (inputs/button
      {:color "primary"
       :variant "contained"
       :href (str "/decision/" slug "/proposals")}
      "Zeige alle Vorschläge")
    (dd/typography {} "Die besten zwei Vorschläge:")
    (list/list {}
      (let [top-2-proposals (take 2 (sort-by ::proposal/pro-votes > proposals))]
        (map ui-top-entry top-2-proposals)))))
