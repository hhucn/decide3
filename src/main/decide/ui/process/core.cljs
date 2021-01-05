(ns decide.ui.process.core
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [decide.models.process :as process]
    [material-ui.data-display :as dd]
    [material-ui.inputs :as inputs]
    [material-ui.layout :as layout]))

(defsc Process [_this {::process/keys [title description]}]
  {:query [::process/slug ::process/title ::process/description]
   :ident ::process/slug
   :route-segment ["decision" ::process/slug]
   :will-enter (fn [app {::process/keys [slug]}]
                 (when slug
                   (let [ident (comp/get-ident Process {::process/slug slug})]
                     (dr/route-deferred ident
                       #(df/load! app ident Process
                          {:post-mutation `dr/target-ready
                           :post-mutation-params {:target ident}})))))
   :use-hooks? true}
  (layout/container {}
    (dd/typography {:component "h1" :variant "h2"} title)
    (dd/typography {:paragraph true} description)
    (inputs/button
      {:color "primary"
       :variant "contained"
       :href "proposals"}
      "Zeige alle Vorschläge")))-