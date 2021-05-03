(ns decide.ui.process.ballot
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [decide.models.opinion :as opinion]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [material-ui.data-display :as dd]
    [material-ui.data-display.list :as list]
    [material-ui.inputs :as inputs]))

(defsc Entry [this {::proposal/keys [id title pro-votes my-opinion]}]
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

(def entry (comp/computed-factory Entry {:keyfn ::proposal/id}))

(defsc Ballot [_ {::process/keys [proposals]}]
  {:query [::process/slug
           {::process/proposals (comp/get-query Entry)}]
   :ident ::process/slug}
  (when (seq proposals)
    (dom/div {}
      (dd/typography {:component :h2 :variant "h5"} (i18n/tr "Your approvals"))
      (list/list {:dense true}
        (->> proposals
          proposal/rank
          (map entry))))))

(def ui-ballot (comp/computed-factory Ballot))