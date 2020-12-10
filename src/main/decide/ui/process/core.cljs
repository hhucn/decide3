(ns decide.ui.process.core
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [decide.models.process :as process]
    [material-ui.layout :as layout]
    [material-ui.surfaces :as surfaces]

    [decide.ui.proposal.NewProposal :as new-proposal]))


(defsc Process [this {:keys [ui/new-proposal-dialog]}]
  {:query         [::process/slug
                   {:ui/new-proposal-dialog (comp/get-query new-proposal/NewProposalFormDialog)}]
   :ident         ::process/slug
   :route-segment ["decision" ::process/slug]
   :will-enter    (fn [app {::process/keys [slug]}]
                    (when slug
                      (let [ident (comp/get-ident Process {::process/slug slug})]
                        (dr/route-deferred ident
                          #(df/load! app ident Process
                             {:post-mutation        `dr/target-ready
                              :post-mutation-params {:target ident}})))))
   :pre-merge     (fn [{:keys [data-tree]}]
                    (merge
                      data-tree
                      {:ui/new-proposal-dialog
                       (comp/get-initial-state new-proposal/NewProposalFormDialog
                         {:id (::process/slug data-tree)})}))}
  (layout/box {:flex 1 :clone true}
    (surfaces/paper {:component :main}
      (new-proposal/ui-new-proposal-form new-proposal-dialog))))