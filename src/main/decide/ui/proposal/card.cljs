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
    [decide.ui.proposal.detail-page :as detail-page]
    [decide.ui.proposal.new-proposal :as new-proposal]
    [decide.utils.breakpoint :as breakpoint]
    [decide.utils.time :as time]
    [material-ui.data-display :as dd]
    [material-ui.data-display.list :as list]
    [material-ui.feedback.dialog :as dialog]
    [material-ui.inputs :as inputs]
    [material-ui.layout :as layout]
    [material-ui.layout.grid :as grid]
    [material-ui.surfaces :as surfaces]
    ["@material-ui/icons/CommentOutlined" :default Comment]
    ["@material-ui/icons/MoreVert" :default MoreVert]
    ["@material-ui/icons/ThumbDownAltOutlined" :default ThumbDownAlt]
    ["@material-ui/icons/ThumbUpAltOutlined" :default ThumbUpAlt]))

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

(defsc Subheader [_ {::proposal/keys [nice-id created parents original-author generation]}]
  {:query [::proposal/id
           ::proposal/nice-id
           ::proposal/created
           ::proposal/generation
           {::proposal/parents (comp/get-query Parent)}
           {::proposal/original-author (comp/get-query proposal/Author)}]
   :ident ::proposal/id}
  (let [author-name (::user/display-name original-author)]
    (dom/span {:classes ["subheader"]
               :style {:height "24px"}}
      (id-part nice-id)
      #_#_" · "
          (when author-name
            (author-part author-name))
      #_#_" · "
          (when (instance? js/Date created)
            (time-part created))
      " · "
      (str "Gen. " generation " ")
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

(defn reject-dialog [this {:keys [slug id open? onClose parents]}]
  (dialog/dialog {:open open? :onClose onClose}
    (dialog/title {} "WHHHHYYYY???")
    (list/list {}
      (list/item
        {:button true
         :onClick (fn []
                    (comp/transact! this
                      [(new-proposal/show
                         {:slug slug
                          :parents [[::proposal/id id]]})
                       (opinion/add {::proposal/id id
                                     :opinion -1})])
                    (onClose))}
        (list/item-text {:primary "I nearly like it"
                         :secondary "Propose an improvement"}))
      (list/item {:button true
                  :onClick (fn []
                             (comp/transact! this
                               [(new-proposal/show
                                  {:slug slug
                                   :parents (mapv #(find % ::proposal/id) parents)})
                                (opinion/add {::proposal/id id
                                              :opinion -1})])
                             (onClose))}
        (list/item-text {:primary "I hate it"
                         :secondary "Propose an alternative"}))
      (list/item {:button true
                  :onClick (fn []
                             (comp/transact! this
                               [(opinion/add {::proposal/id id
                                              :opinion -1})])
                             (onClose))}
        (list/item-text {:primary "Just reject it"
                         :secondary "Hate it and don't be constructive. :-("})))
    (dialog/actions {}
      (inputs/button {:onClick onClose} "Abort"))))

(defn toggle-button [{:keys [icon] :as props}]
  (inputs/icon-button
    (merge
      {:size :small}
      props)
    (dom/create-element icon #js {"fontSize" "small"})))

(defn approve-toggle
  [{:keys [approved?
           onClick
           disabled?]}]
  (toggle-button
    {:aria-label
     (if approved?
       (i18n/trc "Proposal has been approved" "Approved")
       (i18n/trc "Approve a proposal" "Approve"))
     :color (if approved? "primary" "default")
     :disabled disabled?
     :onClick onClick
     :icon ThumbUpAlt}))

(defn reject-toggle
  [{:keys [toggled?
           onClick
           disabled?]}]
  (toggle-button
    {:aria-label
     (if toggled?
       (i18n/trc "Proposal has been rejected by you" "Rejected")
       (i18n/trc "Reject a proposal" "Reject"))
     :color (if toggled? "secondary" "default")
     :disabled disabled?
     :onClick onClick
     :icon ThumbDownAlt}))

(defsc Argument [_ _]
  {:query [::argument/id]
   :ident ::argument/id})

(defsc ProposalCard [this {::proposal/keys [id title body my-opinion arguments pro-votes parents generation]
                           :keys [root/current-session] :as props}
                     {::process/keys [slug]
                      :keys [process-over?
                             card-props
                             max-height]
                      :or {max-height "6rem"}}]
  {:query (fn []
            [::proposal/id
             ::proposal/title ::proposal/body
             ::proposal/my-opinion
             ::proposal/generation
             {::proposal/arguments (comp/get-query Argument)}
             ::proposal/pro-votes
             ::proposal/nice-id
             ::proposal/created
             {::proposal/children 1}
             {::proposal/parents (comp/get-query Parent)}
             {::proposal/original-author (comp/get-query proposal/Author)}
             [:root/current-session '_]])
   :ident ::proposal/id
   :initial-state (fn [{::keys [id title body]}]
                    {::proposal/id id
                     ::proposal/title title
                     ::proposal/body body})
   :use-hooks? true}
  (let [logged-in? (get current-session :session/valid?)
        [reject-open? set-reject-open] (hooks/use-state false)
        proposal-href (hooks/use-memo
                        #(routing/path->absolute-url
                           (dr/into-path ["decision" slug] detail-page/ProposalPage (str id)))
                        [slug id])]
    (surfaces/card
      (merge
        {:raised false
         :variant (when (breakpoint/>=? "sm") :outlined)
         :component :article
         :style {:height "100%"
                 :display :flex
                 :flexDirection "column"}}
        card-props)

      (surfaces/card-action-area {:href proposal-href
                                  :style {:flexGrow 1}}
        (surfaces/card-header
          {:title title
           :titleTypographyProps {:component "h3"}
           :subheader (ui-subheader props)
           :action (inputs/icon-button {:disabled true :size :small}
                     (comp/create-element MoreVert nil nil))})

        (layout/box {:maxHeight max-height
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
        (let [[approved? rejected?] ((juxt pos? neg?) my-opinion)]
          (layout/box {:mx 1 :clone true}
            (grid/container
              {:alignItems :center
               :spacing 1}

              (grid/item {}
                (if process-over?
                  (dom/create-element ThumbUpAlt
                    #js {:fontSize "small"
                         :color (if approved? "primary" "disabled")})
                  (approve-toggle
                    {:approved? approved?
                     :disabled? (or (not logged-in?) process-over?)
                     :onClick #(comp/transact! this [(opinion/add {::proposal/id id
                                                                   :opinion (if approved? 0 1)})])})))

              (grid/item {} (dd/typography {} pro-votes))
              (grid/item {}
                (reject-toggle
                  {:toggled? rejected?
                   :disabled? (or (not logged-in?) process-over?)
                   :onClick #(if-not rejected?
                               (set-reject-open true)
                               (comp/transact! this [(opinion/add {::proposal/id id
                                                                   :opinion (if rejected? 0 -1)})]))}))

              (layout/box {:ml "auto"})
              #_(grid/item {} (dom/create-element Comment #js {"fontSize" "small"}))

              (grid/item {}
                (dd/typography {:variant :body2}
                  (i18n/trf "{count} arguments" {:count (count arguments)})))))))
      (reject-dialog
        this
        {:open? reject-open?
         :id id
         :slug slug
         :parents parents
         :onClose #(set-reject-open false)}))))

(def ui-proposal-card (comp/computed-factory ProposalCard {:keyfn ::proposal/id}))