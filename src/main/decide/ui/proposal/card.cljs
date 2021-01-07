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
    [material-ui.data-display :as dd]
    [material-ui.inputs :as inputs]
    [material-ui.layout :as layout]
    [material-ui.surfaces :as surfaces]
    ["@material-ui/icons/CommentTwoTone" :default Comment]
    ["@material-ui/icons/MoreVert" :default MoreVert]
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
                 :title created}
        (time/nice-string created)))))

(defsc Parent [_ _]
  {:query [::proposal/id]
   :ident ::proposal/id})

(defsc Subheader [_ {::proposal/keys [nice-id created parents original-author]}]
  {:query [::proposal/id
           ::proposal/nice-id
           ::proposal/created
           {::proposal/parents (comp/get-query Parent)}
           {::proposal/original-author (comp/get-query proposal/Author)}]
   :ident ::proposal/id}
  (let [author-name (::user/display-name original-author)]
    (comp/fragment
      (id-part nice-id)
      " · "
      (when author-name
        (author-part author-name))
      " · "
      (when (instance? js/Date created)
        (time-part created))
      " · "
      (case (count parents)
        0 "Original"
        1 "Abgeleitet"
        "Zusammenführung"))))

(def ui-subheader (comp/factory Subheader (:keyfn ::proposal/id)))

(defsc Argument [_ _]
  {:query [::argument/id]
   :ident ::argument/id})

(defsc Proposal [this {::proposal/keys [id title body opinion arguments]
                       :>/keys [subheader]}
                 {::process/keys [slug]}]
  {:query (fn []
            [::proposal/id
             ::proposal/title ::proposal/body
             ::proposal/opinion
             {::proposal/arguments (comp/get-query Argument)}
             {:>/subheader (comp/get-query Subheader)}])
   :ident ::proposal/id
   :initial-state (fn [{:keys [id title body]}]
                    {::proposal/id id
                     ::proposal/title title
                     ::proposal/body body})
   :use-hooks? true}
  (let [proposal-href (hooks/use-memo #(routing/path->absolute-url (dr/path-to detail-page/ProposalPage {::process/slug slug
                                                                                                         ::proposal/id (str id)})))]
    (layout/box {:width "100%" :clone true}
      (surfaces/card
        {:variant :outlined}

        (layout/box {:pb 0 :clone true}
          (surfaces/card-header
            {:title title
             :subheader (ui-subheader subheader)
             :action (inputs/icon-button {:disabled true :size :small}
                       (comp/create-element MoreVert nil nil))}))
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