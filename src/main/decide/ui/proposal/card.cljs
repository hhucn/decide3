(ns decide.ui.proposal.card
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [decide.models.opinion :as opinion]
    [decide.models.opinion.api :as opinion.api]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]
    [decide.models.user.ui :as user.ui]
    [decide.routing :as routing]
    [decide.ui.proposal.detail-page :as detail-page]
    [decide.ui.proposal.new-proposal :as new-proposal]
    [decide.utils.time :as time]
    [mui.data-display :as dd]
    [mui.data-display.list :as list]
    [mui.feedback.dialog :as dialog]
    [mui.inputs :as inputs]
    [mui.layout :as layout]
    [mui.layout.grid :as grid]
    [mui.surfaces.card :as card]
    ["@mui/icons-material/CheckCircleOutlineRounded" :default CheckCircleOutline]
    ["@mui/icons-material/CheckCircleRounded" :default CheckCircle]
    ["@mui/icons-material/Comment" :default Comment]
    ["@mui/icons-material/ThumbDownOutlined" :default ThumbDownOutlined]
    ["@mui/icons-material/ThumbDown" :default ThumbDown]))

(defn- id-part [nice-id]
  (dom/data {:className "proposal-id"
             :value nice-id}
    (str "#" (if (tempid/tempid? nice-id) "?" nice-id))))

(defn- time-part [^js/Date created]
  (comp/fragment
    " "
    (time/nice-time-element created)))

(defsc Author [_ {::user/keys [id display-name]}]
  {:query [::user/id ::user/display-name]
   :ident ::user/id}
  (i18n/trf "by {author}" {:author (dom/address {:key id} (str display-name))}))

(def ui-author (comp/factory Author {:keyfn ::user/id}))

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
  (layout/stack {:className "subheader"
                 :direction :row
                 :spacing 0.5
                 :divider (dom/span {:aria-hidden true} "·")}
    (id-part nice-id)

    (when (and author? original-author)
      (ui-author original-author))

    (when (and created? (instance? js/Date created))
      (time-part created))

    (when (and gen? generation)
      (dd/tooltip
        {:title (i18n/trf "This proposal has a chain of {count} proposals, that lead up to this proposal" {:count generation})}
        (dom/span {} (i18n/trf "Gen. {generation}" {:generation generation}))))

    (when (and type? no-of-parents)
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
                       (opinion.api/add {::proposal/id id
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
                                (opinion.api/add {::proposal/id id
                                                  :opinion -1})])
                             (onClose))}
        (list/item-text {:primary "I hate it"
                         :secondary (i18n/tr "Propose an alternative")}))
      (list/item {:button true
                  :onClick (fn []
                             (comp/transact! this
                               [(opinion.api/add {::proposal/id id
                                                  :opinion -1})])
                             (onClose))}
        (list/item-text {:primary "Just reject it"
                         :secondary "Hate it and don't be constructive. :-("})))
    (dialog/actions {}
      (inputs/button {:onClick onClose} (i18n/tr "Cancel")))))

(defn toggle-button [{:keys [icon] :as props}]
  (inputs/icon-button
    (dissoc props :icon)
    (dom/create-element icon #js {"fontSize" "small"})))

(defn reject-toggle
  [{:keys [toggled?
           onClick
           disabled?]}]
  (toggle-button
    {:aria-label
     (if toggled?
       (i18n/trc "Proposal has been rejected by you" "Rejected")
       (i18n/trc "Reject a proposal" "Reject"))
     :color (if toggled? :error :default)
     :disabled disabled?
     :onClick onClick
     :icon (if toggled? ThumbDown ThumbDownOutlined)}))

(defsc ApproveToggle [_ {:keys [approved? disabled? votes]} {:keys [onToggle]}]
  (dd/tooltip
    {:title (str
              (if approved?
                (i18n/trc "Proposal has been approved" "Approved") ; Always show that you have approved.
                (when-not disabled?                         ; Hide that you can approve, when you can not approve.
                  (i18n/trc "Approve a proposal" "Approve")))
              " [" (i18n/trf "{votes} approved" {:votes votes}) "]")}
    (dom/span {}                                            ; A disabled button would disable the tooltip as well
      (inputs/button
        {:onClick onToggle
         :disabled disabled?
         :color (if (and approved? (not disabled?)) :success :inherit)
         :startIcon (dom/create-element (if approved? CheckCircle CheckCircleOutline))}
        (dd/typography {:color :text.primary, :fontSize :inherit, :variant :button} votes)))))

(def ui-approve-toggle (comp/computed-factory ApproveToggle))

(defsc TotalVotesProcess [_ _]
  {:query [::process/slug :process/total-votes]
   :ident ::process/slug})

(defsc VotingArea [this {::proposal/keys [id pro-votes my-opinion-value my-opinion opinions]
                         {:keys [process/total-votes]} ::proposal/process}
                   {:keys [process]}]
  {:query [::proposal/id
           ::proposal/pro-votes
           {::proposal/process (comp/get-query TotalVotesProcess)}
           ::proposal/my-opinion-value
           {::proposal/my-opinion [::opinion/value :opinion/rank]}
           {::proposal/opinions [::opinion/value
                                 {::opinion/user (comp/get-query user.ui/Avatar)}]}]
   :ident ::proposal/id
   :use-hooks? true}
  (let [logged-in? (comp/shared this :logged-in?)
        disabled? (or (not logged-in?) (not (process/running? process)))
        [reject-open? set-reject-dialog-open] (hooks/use-state false)
        [approved? rejected?] ((juxt pos? neg?) my-opinion-value)]
    (layout/stack
      {:direction :row
       :alignItems :center
       :className "voting-area"
       :spacing 1
       :divider (dd/divider {:orientation :vertical, :flexItem true})}

      (ui-approve-toggle
        {:approved? approved?
         :disabled? disabled?
         :votes pro-votes}
        {:onToggle #(comp/transact! this [(opinion.api/add {::proposal/id id
                                                            :opinion (if approved? 0 1)})])})

      (when (process/allows-rejects? process)
        (comp/fragment
          (when (process/show-reject-dialog? process)
            (reject-dialog
              this
              {:open? reject-open?
               :id id
               :slug (::process/slug process)
               :parents parents
               :onClose #(set-reject-dialog-open false)}))

          (reject-toggle
            {:toggled? rejected?
             :disabled? disabled?
             :onClick
             (fn [_e]
               (if (and (process/show-reject-dialog? process) (not rejected?))
                 (set-reject-dialog-open true)              ; only
                 (comp/transact! this [(opinion.api/add {::proposal/id id
                                                         :opinion (if rejected? 0 -1)})])))})))

      #_(when total-votes
          (let [majority 50
                percent (* 100 (/ pro-votes total-votes))]
            (dd/tooltip {:title (i18n/tr "Voting share")}
              (layout/box {:p 0.5}
                (dd/typography {:color :textSecondary} (Math/round percent) " %")
                (feedback/linear-progress
                  {:variant :determinate
                   :color
                   (cond
                     (zero? percent) :error
                     (< percent majority) :warning
                     (>= percent majority) :success
                     :else :primary)
                   :value percent
                   :sx {:width "100%"}})))))

      (when (process/public-voting? process)
        (->> opinions
          (filter #(pos? (::opinion/value %)))
          (map ::opinion/user)
          (user.ui/avatar-group {:max 5 :sx {:px 1}}))))))

(def ui-voting-area (comp/computed-factory VotingArea {:keyfn ::proposal/id}))

(defsc Process [_ _]
  {:query [::process/slug :process/features ::process/start-time ::process/end-time]
   :ident ::process/slug})

(defsc ProposalCard [this {::proposal/keys [id title body no-of-arguments]
                           :keys [>/subheader >/voting-area ui/current-process]}
                     {::process/keys [slug]
                      :keys [card-props
                             max-height]
                      :or {max-height "6rem"}}]
  {:query [::proposal/id
           ::proposal/title ::proposal/body
           ::proposal/no-of-arguments
           ::proposal/created
           ::proposal/pro-votes
           {:>/subheader (comp/get-query Subheader)}
           {:>/voting-area (comp/get-query VotingArea)}
           {[:ui/current-process '_] (comp/get-query Process)}]
   :ident ::proposal/id
   :initial-state (fn [{::keys [id title body]}]
                    {::proposal/id id
                     ::proposal/title title
                     ::proposal/body body})
   :use-hooks? true}
  (let [proposal-href (hooks/use-memo
                        #(routing/path->absolute-url
                           (dr/into-path ["decision" slug] detail-page/ProposalPage (str id)))
                        [slug id])]
    (card/card
      (merge
        {:component :article
         :style {:height "100%"
                 :display :flex
                 :flexDirection "column"}}
        card-props)

      (card/action-area {:href proposal-href
                         :style {:flexGrow 1}}
        (card/header
          {:title title
           :titleTypographyProps {:component "h3"}
           :subheader (ui-subheader subheader {:type? true :gen? true :created? true :author? false})})
        
        (card/content {:sx {:maxHeight max-height
                            :overflow "hidden"}}
          (dd/typography
            {:component "p"
             :variant "body2"
             :color "textSecondary"
             :style {:whiteSpace "pre-line"}}
            body)))

      (dd/divider {:variant :middle})
      (card/actions {:sx {:px 1.5}}
        (grid/container
          {:alignItems :center
           :justifyContent :space-between
           :direction :row
           :spacing 1}
          (grid/item {:xs true}
            (ui-voting-area voting-area {:process current-process}))

          (grid/item {:xs :auto}
            (dd/tooltip {:title (i18n/trf "{noOf, plural, =1 {# argument} other {# arguments}}" {:noOf no-of-arguments})}
              (inputs/button
                {:startIcon (dom/create-element Comment)
                 :variant :label
                 :href proposal-href}
                (str no-of-arguments)))))))))

(def ui-proposal-card (comp/computed-factory ProposalCard {:keyfn ::proposal/id}))