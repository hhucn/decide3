(ns decide.ui.proposal.card
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [decide.models.opinion :as opinion.legacy]
    [decide.models.opinion.api :as opinion.api]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]
    [decide.opinion :as opinion]
    [decide.routes :as routes]
    [decide.ui.proposal.new-proposal :as new-proposal]
    [decide.ui.user :as user.ui]
    [decide.utils.time :as time]
    [mui.data-display :as dd]
    [mui.data-display.list :as list]
    [mui.feedback :as feedback]
    [mui.feedback.dialog :as dialog]
    [mui.inputs :as inputs]
    [mui.layout :as layout]
    [mui.layout.grid :as grid]
    [mui.surfaces.card :as card]
    [reitit.frontend.easy :as rfe]
    ["@mui/icons-material/CheckCircleOutlineRounded" :default CheckCircleOutline]
    ["@mui/icons-material/CheckCircleRounded" :default CheckCircle]
    ["@mui/icons-material/Comment" :default Comment]
    ["@mui/icons-material/StarOutlineRounded" :default StarOutline]
    ["@mui/icons-material/StarRounded" :default Star]
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
                 :divider (dom/span {:aria-hidden true} "??")}
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
  {:query [::process/slug ::process/no-of-participants]
   :ident ::process/slug})


(defn pro-vote-participant-ratio
  "Display the ratio of `pro-votes`/`no-of-participants` as a percentage and a small bar."
  [{pro-votes ::proposal/pro-votes
    no-of-participants ::process/no-of-participants}]
  (let [majority 0.5
        two-thirds (/ 2 3)
        votes-ratio (/ pro-votes no-of-participants)]
    (dd/tooltip
      {:title (i18n/trf "{ratio, number, ::percent} of participants approve this proposal" {:ratio votes-ratio})}

      (layout/box
        {:py 0.5, :px 1.5}
        (dd/typography
          {:aria-hidden true
           :color (if (<= 1 votes-ratio)
                    :textPrimary
                    :textSecondary)}
          (i18n/t "{ratio, number, ::percent}" {:ratio votes-ratio}))
        (feedback/linear-progress
          {:variant :determinate
           :aria-hidden true
           :color
           (cond
             (< votes-ratio majority) :error
             (< votes-ratio two-thirds) :warning
             (<= two-thirds votes-ratio) :success
             :else :gold)
           :value (* 100 votes-ratio)
           :sx {:width "100%"}})))))


(defsc VotingArea [this {::proposal/keys [id pro-votes my-opinion-value my-opinion opinions favorite-votes]
                         {::process/keys [no-of-participants]} ::proposal/process}
                   {:keys [process show-ratio?]
                    :or {show-ratio? false}}]
  {:query [::proposal/id
           ::proposal/pro-votes
           ::proposal/favorite-votes
           {::proposal/process (comp/get-query TotalVotesProcess)}
           ::proposal/my-opinion-value
           {::proposal/my-opinion [::opinion.legacy/value :opinion/rank]}
           {::proposal/opinions [::opinion.legacy/value
                                 {::opinion.legacy/user (comp/get-query user.ui/Avatar)}]}]
   :ident ::proposal/id
   :use-hooks? true}
  (let [logged-in? (comp/shared this :logged-in?)
        disabled? (or (not logged-in?) (not (process/running? process)))
        [reject-open? set-reject-dialog-open] (hooks/use-state false)
        [approved? rejected?] ((juxt pos? neg?) my-opinion-value)
        favorite? (or (opinion/favorite-value? my-opinion-value) (opinion.legacy/favorite? my-opinion))]
    (layout/stack
      {:direction :row
       :alignItems :center
       :className "voting-area"
       :spacing 0.5
       :divider (dd/divider {:orientation :vertical, :flexItem true})}

      (ui-approve-toggle
        {:approved? approved?
         :disabled? disabled?
         :votes pro-votes}
        {:onToggle #(comp/transact! this [(opinion.api/add {::proposal/id id
                                                            :opinion (if approved? 0 1)})])})

      (inputs/button
        {:startIcon (if favorite?
                      (dom/create-element Star)
                      (dom/create-element StarOutline))
         :disabled disabled?
         :color :gold
         :onClick #(comp/transact! this [(opinion.api/add {::proposal/id id
                                                           :opinion (if favorite? 1 2)})])}
        (dd/typography {:color :text.primary, :fontSize :inherit, :variant :button}
          (or favorite-votes 0)))

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

      (when (and show-ratio? no-of-participants)
        (pro-vote-participant-ratio
          {::proposal/pro-votes pro-votes
           ::process/no-of-participants no-of-participants}))

      (when (process/public-voting? process)
        (->> opinions
          (filter #(pos? (::opinion.legacy/value %)))
          (map ::opinion.legacy/user)
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
           ::proposal/favorite-votes
           {:>/subheader (comp/get-query Subheader)}
           {:>/voting-area (comp/get-query VotingArea)}
           {[:ui/current-process '_] (comp/get-query Process)}]
   :ident ::proposal/id
   :initial-state (fn [{::keys [id title body]}]
                    {::proposal/id id
                     ::proposal/title title
                     ::proposal/body body})}
  (card/card
    (merge
      {:component :article
       :style {:height "100%"
               :display :flex
               :flexDirection "column"}}
      card-props)

    (card/action-area {:href (rfe/href ::routes/proposal-detail-page {:process/slug slug
                                                                      :proposal/id id})
                       :sx {:flexGrow 1}}
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
        (grid/item {:xs :auto}
          (ui-voting-area voting-area {:process current-process
                                       :show-ratio? true}))

        (grid/item {:xs :auto}
          (layout/stack {:direction :row}
            (dd/tooltip {:title (i18n/trf "{noOf, plural, =1 {# argument} other {# arguments}}" {:noOf no-of-arguments})}
              (inputs/button
                {:startIcon (dom/create-element Comment)
                 :variant :label
                 :href (rfe/href ::routes/proposal-detail-page {:process/slug slug :proposal/id id})}
                (str no-of-arguments)))))))))

(def ui-proposal-card (comp/computed-factory ProposalCard {:keyfn ::proposal/id}))
