(ns decide.ui.proposal.card
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [decide.models.argument :as argument]
    [decide.models.user :as user]
    [decide.models.opinion :as opinion]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.routing :as routing]
    [decide.ui.proposal.detail-page :as detail-page]
    [decide.ui.common.time :as time]
    [decide.utils :as utils]
    [material-ui.data-display :as dd]
    [material-ui.inputs :as inputs]
    [material-ui.layout :as layout]
    [material-ui.surfaces :as surfaces]
    ["@material-ui/icons/CommentTwoTone" :default Comment]
    ["@material-ui/icons/ThumbDownAltTwoTone" :default ThumbDownAltTwoTone]
    ["@material-ui/icons/ThumbUpAltTwoTone" :default ThumbUpAltTwoTone]
    [com.fulcrologic.fulcro.dom :as dom]
    [taoensso.timbre :as log]))

(defn id-part [proposal-id]
  (dom/data {:className "proposal-id"
             :value     proposal-id}
    (str "#" (if (tempid/tempid? proposal-id) "?" proposal-id))))

(defn author-part [author-name]
  (comp/fragment "von " (dom/address (str author-name))))

(defn time-part [^js/Date created]
  (let [iso-string (.toISOString created)]
    (comp/fragment
      " "
      (dom/time {:dateTime iso-string
                 :title    created}
        (time/nice-string created)))))

(defn subheader [{::proposal/keys [nice-id created original-author]}]
  (let [author-name (::user/display-name original-author)]
    (comp/fragment
      (id-part nice-id)
      " · "
      (when author-name
        (author-part author-name))
      " · "
      (when (instance? js/Date created)
        (time-part created)))))

(defsc Argument [_ _]
  {:query [::argument/id]
   :ident ::argument/id})

(defsc Proposal [this {::proposal/keys [id nice-id title body opinion arguments created original-author] :as props}
                 {::process/keys [slug]}]
  {:query (fn []
            [::proposal/id
             ::proposal/nice-id
             ::proposal/title ::proposal/body
             ::proposal/pro-votes ::proposal/con-votes
             ::proposal/created
             ::proposal/opinion
             {::proposal/arguments (comp/get-query Argument)}
             {::proposal/parents '...}
             {::proposal/original-author (comp/get-query proposal/Author)}])
   :ident ::proposal/id
   :initial-state (fn [{:keys [id title body]}]
                    {::proposal/id id
                     ::proposal/title title
                     ::proposal/body body
                     ::proposal/pro-votes 0
                     ::proposal/con-votes 0})
   :use-hooks? true}
  (let [proposal-href (hooks/use-memo #(routing/path->url (dr/path-to detail-page/ProposalPage {::process/slug slug
                                                                                                ::proposal/id (str id)})))]
    (layout/box {:width "100%" :clone true}
      (surfaces/card
        {:variant :outlined}

        (layout/box {:pb 0 :clone true}
          (surfaces/card-header
            {:title title
             :subheader (subheader props)}))
        (surfaces/card-content {}
          (dd/typography
            {:component "p"
             :variant "body2"
             :color "textSecondary"
             :style {:whiteSpace "pre-line"}}
            body))

        (surfaces/card-actions {}
          (inputs/button-group
            {:size :small
             :variant :text
             :disableElevation true}
            (inputs/button {:color (if (pos? opinion) "primary" "default")
                            :onClick #(comp/transact! this [(opinion/add {::proposal/id id
                                                                          :opinion (if (pos? opinion) 0 +1)})])
                            :startIcon (comp/create-element ThumbUpAltTwoTone nil nil)}
              "Zustimmen")
            (inputs/button {:color (if (neg? opinion) "primary" "default")
                            :aria-label "Ablehnen"
                            :onClick #(comp/transact! this [(opinion/add {::proposal/id id
                                                                          :opinion (if (neg? opinion) 0 -1)})])
                            :startIcon (comp/create-element ThumbDownAltTwoTone nil nil)}))

          (layout/box {:style {:marginLeft "auto"}})
          (dd/typography {:variant :button} (count arguments) " Argumente")
          (inputs/button {:component "a"
                          :color :primary
                          :href proposal-href} "Mehr"))))))

(def ui-proposal (comp/computed-factory Proposal {:keyfn ::proposal/id}))