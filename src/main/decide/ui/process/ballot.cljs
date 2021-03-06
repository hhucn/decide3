(ns decide.ui.process.ballot
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [decide.models.opinion.api :as opinion.api]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [mui.data-display :as dd]
    [mui.data-display.list :as list]
    [mui.inputs :as inputs]))

(defsc Entry [this {::proposal/keys [id title pro-votes my-opinion-value]}]
  {:query [::proposal/id
           ::proposal/title
           ::proposal/pro-votes
           ::proposal/my-opinion-value]
   :ident ::proposal/id}
  (let [approved? (pos? my-opinion-value)]
    (list/item
      {:button true
       :component :a
       :href (str "proposal/" id)}
      (list/item-icon {}
        (inputs/checkbox
          {:edge :start
           :checked approved?
           :onClick
           (fn [e]
             (evt/stop-propagation! e)
             (comp/transact! this [(opinion.api/add {::proposal/id id
                                                     :opinion (if approved? 0 1)})]))}))

      (list/item-text
        {:primary title
         :secondary (i18n/trf "Approvals: {pros}" {:pros pro-votes})}))))

(def entry (comp/computed-factory Entry {:keyfn ::proposal/id}))

(defn header []
  (dd/typography {:component :h2 :variant "h5"} (i18n/tr "Your approvals")))

(defsc Ballot [_ {::process/keys [proposals]}]
  {:query [::process/slug
           {::process/proposals (comp/get-query Entry)}]
   :ident ::process/slug}
  (list/list {:dense true}
    (->> proposals
      proposal/rank
      (map entry))))

(def ui-ballot (comp/computed-factory Ballot))