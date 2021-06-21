(ns decide.ui.proposal.card
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [decide.models.opinion :as opinion]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]
    [decide.routing :as routing]
    [decide.ui.proposal.detail-page :as detail-page]
    [decide.ui.proposal.new-proposal :as new-proposal]
    [decide.utils.time :as time]
    [material-ui.data-display :as dd]
    [material-ui.data-display.list :as list]
    [material-ui.feedback.dialog :as dialog]
    [material-ui.inputs :as inputs]
    [material-ui.layout :as layout]
    [material-ui.layout.grid :as grid]
    [material-ui.surfaces :as surfaces]
    ["@material-ui/icons/Comment" :default Comment]
    ["@material-ui/icons/MoreVert" :default MoreVert]
    ["@material-ui/icons/ThumbDownAltOutlined" :default ThumbDownOutlined]
    ["@material-ui/icons/ThumbDownAlt" :default ThumbDown]
    ["@material-ui/icons/ThumbUpAltOutlined" :default ThumbUpOutlined]
    ["@material-ui/icons/ThumbUpAlt" :default ThumbUp]))

(defn id-part [proposal-id]
  (dom/data {:className "proposal-id"
             :value proposal-id}
    (str "#" (if (tempid/tempid? proposal-id) "?" proposal-id))))

(defn time-part [^js/Date created]
  (comp/fragment
    " "
    (time/nice-time-element created)))

(defsc Author [_ {::user/keys [id display-name]}]
  {:query [::user/id ::user/display-name]
   :ident ::user/id}
  (i18n/trf "by {author}" {:author (dom/address {:key id} (str display-name))}))

(def ui-author (comp/factory Author {:keyfn ::user/id}))

(defsc Parent [_ _]
  {:query [::proposal/id]
   :ident ::proposal/id})

(defsc Subheader [_ {::proposal/keys [nice-id created no-of-parents original-author generation]}
                  {:keys [author? created? gen? type?]
                   :or {author? false
                        created? false
                        gen? false
                        type? false}}]
  {:query [::proposal/id
           ::proposal/nice-id
           ::proposal/created
           ::proposal/generation
           ::proposal/no-of-parents
           {::proposal/original-author (comp/get-query Author)}]
   :ident ::proposal/id}
  (dom/span {:classes ["subheader"]
             :style {:height "24px"}}
    (id-part nice-id)

    (when (and author? original-author)
      (comp/fragment
        " · "
        (ui-author original-author)))
    " · "
    (when (and created? (instance? js/Date created))
      (time-part created))
    (when gen?
      (comp/fragment
        " · "
        (dd/tooltip
          {:title (i18n/trf "This proposal has a chain of {count} proposals, that lead up to this proposal" {:count generation})}
          (dom/span {} (i18n/trf "Gen. {generation}" {:generation generation})))))
    (when type?
      (case no-of-parents
        0 nil
        1
        (dd/tooltip {:title (i18n/tr "This proposal is derived from one other proposal")}
          (dd/chip
            {:size :small
             :color :primary
             :label (i18n/trc "Type of proposal" "Fork")}))

        (dd/tooltip {:title (i18n/tr "This proposal is derived from two or more other proposals")}
          (dd/chip
            {:size :small
             :color :secondary
             :label (i18n/trc "Type of proposal" "Merge")}))))))

(def ui-subheader (comp/computed-factory Subheader (:keyfn ::proposal/id)))

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
                         :secondary (i18n/tr "Propose an improvement")}))
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
                         :secondary (i18n/tr "Propose an alternative")}))
      (list/item {:button true
                  :onClick (fn []
                             (comp/transact! this
                               [(opinion/add {::proposal/id id
                                              :opinion -1})])
                             (onClose))}
        (list/item-text {:primary "Just reject it"
                         :secondary "Hate it and don't be constructive. :-("})))
    (dialog/actions {}
      (inputs/button {:onClick onClose} (i18n/tr "Cancel")))))

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
     :icon (if approved? ThumbUp ThumbUpOutlined)}))

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
     :icon (if toggled? ThumbDown ThumbDownOutlined)}))

(defsc ProposalCard [this {::proposal/keys [id title body my-opinion pro-votes parents no-of-arguments]
                           :keys [root/current-session >/subheader]}
                     {::process/keys [slug]
                      :keys [process-over?
                             card-props
                             max-height
                             features]
                      :or {max-height "6rem"
                           features #{}}}]
  {:query (fn []
            [::proposal/id
             ::proposal/title ::proposal/body
             ::proposal/my-opinion
             ::proposal/no-of-arguments
             ::proposal/pro-votes
             ::proposal/created
             {::proposal/children 1}
             {::proposal/parents (comp/get-query Parent)}
             [:root/current-session '_]
             {:>/subheader (comp/get-query Subheader)}])
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
           :subheader (ui-subheader subheader {:type? true :gen? true :created? true :author? false})
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
                  (dom/create-element ThumbUpOutlined
                    #js {:fontSize "small"
                         :color (if approved? "primary" "disabled")})
                  (approve-toggle
                    {:approved? approved?
                     :disabled? (or (not logged-in?) process-over?)
                     :onClick #(comp/transact! this [(opinion/add {::proposal/id id
                                                                   :opinion (if approved? 0 1)})])})))

              (grid/item {} (dd/typography {} pro-votes))
              (when (features :feature/rejects)
                (grid/item {}
                  (reject-toggle
                    {:toggled? rejected?
                     :disabled? (or (not logged-in?) process-over?)
                     :onClick
                     (fn [_e]
                       (if (and (features :feature/reject-popup) (not rejected?))
                         (set-reject-open true)             ; only
                         (comp/transact! this [(opinion/add {::proposal/id id
                                                             :opinion (if rejected? 0 -1)})])))})))

              (layout/box {:ml "auto"})

              (grid/item {}
                (inputs/button
                  {:startIcon (dom/create-element Comment)
                   :size :small
                   :href proposal-href}
                  (str no-of-arguments)))))))

      (when (features :feature/reject-popup)
        (reject-dialog
          this
          {:open? reject-open?
           :id id
           :slug slug
           :parents parents
           :onClose #(set-reject-open false)})))))

(def ui-proposal-card (comp/computed-factory ProposalCard {:keyfn ::proposal/id}))