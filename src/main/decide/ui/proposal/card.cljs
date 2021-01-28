(ns decide.ui.proposal.card
  (:require
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [decide.models.argument :as argument]
    [decide.models.opinion :as opinion]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]
    [decide.routing :as routing]
    [decide.ui.common.time :as time]
    [decide.ui.proposal.detail-page :as detail-page]
    [material-ui.data-display :as dd]
    [material-ui.inputs :as inputs]
    [material-ui.layout :as layout]
    [material-ui.surfaces :as surfaces]
    ["@material-ui/icons/CommentTwoTone" :default Comment]
    ["@material-ui/icons/MoreVert" :default MoreVert]
    ["@material-ui/icons/ThumbDownAltTwoTone" :default ThumbDownAltTwoTone]
    ["@material-ui/icons/ThumbUpAltTwoTone" :default ThumbUpAltTwoTone]
    [taoensso.timbre :as log]))

(defn id-part [proposal-id]
  (dom/data {:className "proposal-id"
             :value proposal-id}
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
      (let [no-of-parents (count parents)]
        (when (pos? no-of-parents)
          (str " · "
            (case no-of-parents
              1 "Fork"
              "Merge")))))))

(def ui-subheader (comp/factory Subheader (:keyfn ::proposal/id)))

(defsc Argument [_ _]
  {:query [::argument/id]
   :ident ::argument/id})

(defsc ProposalCard [this {::proposal/keys [id title body my-opinion arguments pro-votes]
                           :keys [root/current-session] :as props}
                     {::process/keys [slug]}]
  {:query (fn []
            [::proposal/id
             ::proposal/title ::proposal/body
             ::proposal/my-opinion
             {::proposal/arguments (comp/get-query Argument)}
             ::proposal/pro-votes
             ::proposal/nice-id
             ::proposal/created
             {::proposal/parents (comp/get-query Parent)}
             {::proposal/original-author (comp/get-query proposal/Author)}
             [:root/current-session '_]])
   :ident ::proposal/id
   :initial-state (fn [{:keys [id title body]}]
                    {::proposal/id id
                     ::proposal/title title
                     ::proposal/body body})
   :use-hooks? true}
  (let [logged-in? (get current-session :session/valid?)
        proposal-href (hooks/use-memo
                        #(routing/path->absolute-url
                           (dr/into-path ["decision" slug] detail-page/ProposalPage (str id)))
                        [slug id])]
    (surfaces/card
      {:raised false
       :component :article}

      (surfaces/card-header
        {:title title
         :titleTypographyProps {:component "h3"}
         :subheader (ui-subheader props)
         :action (inputs/icon-button {:disabled true :size :small}
                   (comp/create-element MoreVert nil nil))})

      (surfaces/card-content {}
        (dd/typography
          {:component "p"
           :variant "body2"
           :color "textSecondary"
           :style {:whiteSpace "pre-line"}}
          body))

      (surfaces/card-actions {}
        (layout/box {:color "success.main"} (dd/typography {:variant :button} pro-votes))

        (let [approved? (pos? my-opinion)]
          (inputs/button
            {:disabled (not logged-in?)
             :color (if approved? "primary" "default")
             :variant :text                                 ;(if approved? :contained :text)
             :onClick #(comp/transact! this [(opinion/add {::proposal/id id
                                                           :opinion (if approved? 0 +1)})])
             :startIcon (comp/create-element ThumbUpAltTwoTone nil nil)}
            (if-not approved? "Zustimmen" "Zugestimmt")))
        #_(inputs/button-group
            {:size :small
             :variant :text
             :disableElevation true}
            (layout/box {:color "green"} (dd/typography {:variant :button :color "inherit"} pro-votes))
            (inputs/button {:color (if (neg? my-opinion) "primary" "default")
                            :aria-label "Ablehnen"
                            :onClick #(comp/transact! this [(opinion/add {::proposal/id id
                                                                          :opinion (if (neg? my-opinion) 0 -1)})])
                            :startIcon (comp/create-element ThumbDownAltTwoTone nil nil)}))

        (layout/box {:style {:marginLeft "auto"}}
          (dd/typography {:variant :button} (count arguments) " Argumente")

          (inputs/button {:component "a"
                          :color :primary
                          :href proposal-href} "Mehr"))))))

(def ui-proposal-card (comp/computed-factory ProposalCard {:keyfn ::proposal/id}))