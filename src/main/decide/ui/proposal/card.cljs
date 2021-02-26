(ns decide.ui.proposal.card
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
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
    [decide.utils.time :as time]
    [decide.ui.proposal.detail-page :as detail-page]
    [material-ui.data-display :as dd]
    [material-ui.inputs :as inputs]
    [material-ui.layout :as layout]
    [material-ui.surfaces :as surfaces]
    ["@material-ui/icons/CommentTwoTone" :default Comment]
    ["@material-ui/icons/MoreVert" :default MoreVert]
    ["@material-ui/icons/ThumbDownAltTwoTone" :default ThumbDownAltTwoTone]
    ["@material-ui/icons/ThumbUpAltTwoTone" :default ThumbUpAltTwoTone]
    [material-ui.layout.grid :as grid]))

(defn id-part [proposal-id]
  (dom/data {:className "proposal-id"
             :value proposal-id}
    (str "#" (if (tempid/tempid? proposal-id) "?" proposal-id))))

(defn author-part [author-name]
  (apply comp/fragment (i18n/trf "by {author}" {:author (dom/address (str author-name))})))

(defn time-part [^js/Date created]
  (comp/fragment
    " "
    (time/nice-time-element created)))

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
    (dom/span {:classes ["subheader"]}
      (id-part nice-id)
      " · "
      (when author-name
        (author-part author-name))
      " · "
      (when (instance? js/Date created)
        (time-part created))
      (let [no-of-parents (count parents)]
        (case no-of-parents
          0 nil
          1 (dd/chip
              {:size :small
               :color :primary
               :label (i18n/trc "Type of proposal" "Fork")})
          (dd/chip
            {:size :small
             :color :secondary
             :label (i18n/trc "Type of proposal" "Merge")}))))))

(def ui-subheader (comp/factory Subheader (:keyfn ::proposal/id)))

(defsc Argument [_ _]
  {:query [::argument/id]
   :ident ::argument/id})

(defsc ProposalCard [this {::proposal/keys [id title body my-opinion arguments pro-votes]
                           :keys [root/current-session] :as props}
                     {::process/keys [slug]
                      :keys [process-over?]}]
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
       :component :article
       :style {:height "100%"
               :display :flex
               :flexDirection "column"}}

      (surfaces/card-action-area {:href proposal-href
                                  :style {:flexGrow 1}}
        (surfaces/card-header
          {:title title
           :titleTypographyProps {:component "h3"}
           :subheader (ui-subheader props)
           :action (inputs/icon-button {:disabled true :size :small}
                     (comp/create-element MoreVert nil nil))})

        (layout/box {:maxHeight "6rem"
                     :overflow "hidden"
                     :clone true}
          (surfaces/card-content {}
            (dd/typography
              {:component "p"
               :variant "body2"
               :color "textSecondary"
               :style {:whiteSpace "pre-line"}}
              body))))

      (dd/divider {:variant :middle})
      (surfaces/card-actions {}
        (let [approved? (pos? my-opinion)]
          (layout/box {:mx 1 :clone true}
            (grid/container
              {:alignItems :center
               :spacing 1}

              (grid/item {}
                (inputs/icon-button
                  {:size :small
                   :aria-label
                   (if-not approved?
                     (i18n/trc "Approve a proposal" "Approve")
                     (i18n/trc "Proposal has been approved" "Approved"))
                   :color (if approved? "primary" "default")
                   :disabled (or (not logged-in?) process-over?)
                   :onClick #(comp/transact! this [(opinion/add {::proposal/id id
                                                                 :opinion (if approved? 0 1)})])}
                  (dom/create-element ThumbUpAltTwoTone #js {"fontSize" "small"})))
              (grid/item {} (dd/typography {} pro-votes))


              (layout/box {:ml "auto" :clone true}
                (grid/item {}
                  (dd/typography {:variant :body2}
                    (i18n/trf "{count} arguments" {:count (count arguments)})))))))))))

(def ui-proposal-card (comp/computed-factory ProposalCard {:keyfn ::proposal/id}))