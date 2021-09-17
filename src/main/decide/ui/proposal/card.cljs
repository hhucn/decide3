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
    [mui.surfaces :as surfaces]
    ["@mui/icons-material/Comment" :default Comment]
    ["@mui/icons-material/ThumbDownAltOutlined" :default ThumbDownOutlined]
    ["@mui/icons-material/ThumbDownAlt" :default ThumbDown]
    ["@mui/icons-material/ThumbUpAltOutlined" :default ThumbUpOutlined]
    ["@mui/icons-material/ThumbUpAlt" :default ThumbUp]
    [mui.surfaces.card :as card]))

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
    (when (and gen? generation)
      (comp/fragment
        " · "
        (dd/tooltip
          {:title (i18n/trf "This proposal has a chain of {count} proposals, that lead up to this proposal" {:count generation})}
          (dom/span {} (i18n/trf "Gen. {generation}" {:generation generation})))))
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

(defn disabled-approve-toggle [{:keys [approved?]}]
  (dom/create-element ThumbUpOutlined
    #js {:fontSize "small"
         :color (if approved? "primary" "disabled")}))

(defn avatar-group [{:keys [max] :as props} children]
  (dd/avatar-group (update props :max inc)
    (doall
      (let [[to-display overflow] (split-at (dec max) children)]
        (concat
          (map #(user.ui/ui-avatar % {:tooltip? true}) to-display)
          [(cond
             (= 1 (count overflow)) (user.ui/ui-avatar (first overflow) {:tooltip? true})

             ;; Display "+42" overflow Avatar
             (< 1 (count overflow))
             (dd/tooltip {:title (list/list {:dense true}
                                   (doall
                                     (for [{::user/keys [display-name]} overflow]
                                       (list/item {:disableGutters true} display-name))))
                          :arrow true}
               (dd/avatar {} (str "+" (count overflow)))))])))))

(defsc VotingArea [this {::proposal/keys [id pro-votes my-opinion opinions]}
                   {:keys [process]}]
  {:query [::proposal/id
           ::proposal/pro-votes
           ::proposal/my-opinion-value
           {::proposal/my-opinion [::opinion/value :opinion/rank]}
           {::proposal/opinions [::opinion/value
                                 {::opinion/user (comp/get-query user.ui/Avatar)}]}]
   :ident ::proposal/id
   :use-hooks? true}
  (let [logged-in? (comp/shared this :logged-in?)
        disabled? (or (not logged-in?) (not (process/running? process)))
        [reject-open? set-reject-dialog-open] (hooks/use-state false)
        [approved? rejected?] ((juxt pos? neg?) (::opinion/value my-opinion))]
    (grid/container {:alignItems :center}
      (grid/item {}
        (if (process/running? process)
          (approve-toggle
            {:approved? approved?
             :disabled? disabled?
             :onClick #(comp/transact! this [(opinion.api/add {::proposal/id id
                                                               :opinion (if approved? 0 1)})])})
          (disabled-approve-toggle approved?)))

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

          (grid/item {}
            (reject-toggle
              {:toggled? rejected?
               :disabled? disabled?
               :onClick
               (fn [_e]
                 (if (and (process/show-reject-dialog? process) (not rejected?))
                   (set-reject-dialog-open true)            ; only
                   (comp/transact! this [(opinion.api/add {::proposal/id id
                                                           :opinion (if rejected? 0 -1)})])))}))))


      (grid/item {}
        (if (process/public-voting? process)
          (->> opinions
            (filter #(pos? (::opinion/value %)))
            (map ::opinion/user)
            (user.ui/avatar-group {:max 5}))
          (dd/typography {:variant :body1
                          :aria-label (i18n/trc "[ARIA]" "pro votes")}
            pro-votes))))))

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
        {:raised false
         :component :article
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
      (card/actions {}
        (grid/container
          {:alignItems :center
           :justifyContent :space-between
           :direction :row
           :spacing 1}
          (grid/item {:xs :auto}
            (ui-voting-area voting-area {:process current-process}))

          (grid/item {}
            (inputs/button
              {:startIcon (dom/create-element Comment)
               :href proposal-href}
              (str no-of-arguments))))))))

(def ui-proposal-card (comp/computed-factory ProposalCard {:keyfn ::proposal/id}))