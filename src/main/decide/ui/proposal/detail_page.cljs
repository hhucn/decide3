(ns decide.ui.proposal.detail-page
  (:require
    [com.fulcrologic.fulcro.algorithms.merge :as mrg]
    [com.fulcrologic.fulcro.algorithms.normalized-state :as norm]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.models.argument :as argument]
    [decide.models.user :as user]
    [decide.routing :as routing]
    [decide.ui.proposal.new-proposal :as new-proposal]
    [decide.utils :as utils]
    [material-ui.data-display :as dd]
    [material-ui.feedback :as feedback]
    [material-ui.inputs :as inputs]
    [material-ui.layout :as layout]
    [material-ui.surfaces :as surfaces]
    [com.fulcrologic.fulcro.algorithms.react-interop :as interop]
    ["@material-ui/icons/ThumbUpAltTwoTone" :default ThumbUpAltTwoTone]
    ["@material-ui/icons/ThumbDownAltTwoTone" :default ThumbDownAltTwoTone]
    ["@material-ui/icons/Close" :default Close]
    ["@material-ui/icons/MergeType" :default MergeType]
    ["@material-ui/icons/CallSplit" :default CallSplit]
    ["@material-ui/core/LinearProgress" :default LinearProgress]
    ["@material-ui/icons/Send" :default Send]
    ["@material-ui/core/styles" :refer (withStyles useTheme)]
    [com.fulcrologic.fulcro.dom :as dom]
    [taoensso.timbre :as log]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro.application :as app]))

(declare ProposalPage)

(defsc Parent [_this {::proposal/keys [id title]}]
  {:query [::proposal/id ::proposal/title]
   :ident ::proposal/id}
  (dd/list-item
    {:button    true
     :component "a"
     :href      id}
    (dd/list-item-avatar {} (str "#" id))
    (dd/list-item-text {} (str title))))

(def ui-parent (comp/computed-factory Parent {:keyfn ::proposal/id}))

;; region Vote scale
(defn percent-of-pro-votes [pro-votes con-votes]
  (if (zero? pro-votes)
    0
    (* 100 (/ pro-votes (+ pro-votes con-votes)))))

(def vote-linear-progress
  (interop/react-factory
    ((withStyles
       (fn [theme]
         (clj->js {:barColorPrimary {:backgroundColor (.. theme -palette -success -main)}
                   :colorPrimary    {:backgroundColor (.. theme -palette -error -main)}})))
     LinearProgress)))

(defn vote-scale [{::proposal/keys [pro-votes con-votes]
                   :or             {pro-votes 0 con-votes 0}}]
  (layout/grid {:container  true
                :alignItems :center
                :justify    :space-between
                :wrap       :nowrap}
    (layout/grid {:item true :xs true :align :center} (str pro-votes))
    (layout/grid {:item true :xs 9}
      (vote-linear-progress
        {:variant "determinate"
         :value   (percent-of-pro-votes pro-votes con-votes)}))
    (layout/grid {:item true :xs true :align :center} (str con-votes))))
;; endregion

(defn proposal-section [title & children]
  (comp/fragment
    (dd/divider {:light true})
    (apply layout/box {:mt 1 :mb 2}
      (dd/typography {:variant "h6" :color "textSecondary" :component "h3"} title)
      children)))

(defsc Author [_ {::user/keys [display-name]}]
  {:query [:user/id ::user/display-name]
   :ident :user/id}
  (dom/span display-name))

(def ui-argument-author (comp/factory Author))

(defsc ArgumentRow [_ {::argument/keys [content author]}]
  {:query [::argument/id ::argument/content {::argument/author (comp/get-query Author)}]
   :ident ::argument/id}
  (dd/list-item {}
    (dd/list-item-text {:primary   content
                        :secondary (comp/fragment "Von " (ui-argument-author author))})))

(def ui-comment-row (comp/computed-factory ArgumentRow {:keyfn ::argument/id}))

(defmutation add-argument [{::proposal/keys [id]
                            :keys [temp-id content]}]
  (action [{:keys [state]}]
    (let [new-comment-data {::argument/id      temp-id
                            ::argument/content content}]
      (norm/swap!-> state
        (mrg/merge-component ArgumentRow new-comment-data
          :append (conj (comp/get-ident ProposalPage {::proposal/id id}) ::proposal/arguments)))))
  (remote [env]
    (-> env
      (m/with-server-side-mutation proposal/add-argument)
      (m/returning ArgumentRow))))

(defsc NewCommentLine [this _ {::proposal/keys [id]}]
  {:use-hooks? true}
  (let [[new-argument set-new-argument] (hooks/use-state "")
        submit (hooks/use-callback
                 (fn [e]
                   (evt/prevent-default! e)
                   (comp/transact! this [(add-argument {::proposal/id id
                                                        :temp-id      (tempid/tempid)
                                                        :content      new-argument})])
                   (set-new-argument ""))
                 [new-argument])]
    (layout/grid {:container true
                  :component "form"
                  :onSubmit  submit}
      (inputs/textfield
        {:fullWidth  true
         :label      "Neues Argument"
         :variant    :outlined
         :value      new-argument
         :onChange   #(set-new-argument (evt/target-value %))
         :inputProps {:aria-label "Neues Argument"}
         :InputProps {:endAdornment (inputs/icon-button {:type       :submit
                                                         :aria-label "Absenden"}
                                      (comp/create-element Send nil nil))}}))))

(def ui-new-comment-line (comp/computed-factory NewCommentLine))

(defsc ProposalPage [this {::proposal/keys [id title body parents arguments]
                           ::process/keys  [slug]
                           :as             props}]
  {:query         [::proposal/id ::proposal/title ::proposal/body
                   ::process/slug
                   {::proposal/parents (comp/get-query Parent)}
                   ::proposal/pro-votes ::proposal/con-votes
                   {::proposal/original-author (comp/get-query Author)}
                   {::proposal/arguments (comp/get-query ArgumentRow)}]
   :ident         ::proposal/id
   :route-segment ["decision" ::process/slug "proposal" ::proposal/id]
   :will-enter    (fn will-enter-proposal-page
                    [app {::proposal/keys [id]}]
                    (let [ident (comp/get-ident ProposalPage {::proposal/id id})]
                      (if (get-in (app/current-state app) ident)
                        (do
                          (df/load! app ident ProposalPage) ; just to refresh
                          (dr/route-immediate ident))
                        (dr/route-deferred ident
                          #(df/load! app ident ProposalPage
                             {:post-mutation        `dr/target-ready
                              :post-mutation-params {:target ident}})))))
   :use-hooks?    true}
  (let [[open? set-open] (hooks/use-state true)]
    (feedback/dialog
      {:open       open?
       :fullScreen (utils/<=-breakpoint? "xs")
       :fullWidth  true
       :maxWidth   "md"
       :onClose    #(set-open false)
       :onExiting  #(js/window.history.back)}               ; TODO don't do this

      (surfaces/toolbar {:variant "dense"}
        (inputs/icon-button
          {:edge       :start
           :color      :inherit
           :aria-label "back"
           :onClick    #(set-open false)}
          (comp/create-element Close nil nil))
        (feedback/dialog-title {} title))
      (feedback/dialog-content {}
        (dd/typography {:variant   "body1"
                        :paragraph true}
          body)
        (inputs/button
          {:color     :primary
           :variant   :outlined
           :onClick   #(comp/transact!! this [(new-proposal/show {:id      slug
                                                                  :parents [(comp/get-ident this)]})])
           :startIcon (layout/box {:clone true :css {:transform "rotate(.5turn)"}} (comp/create-element CallSplit nil nil))
           :endIcon   (layout/box {:clone true :css {:transform "rotate(.5turn)"}} (comp/create-element MergeType nil nil))}
          "Fork / Merge")

        (when-not (empty? parents)
          (proposal-section
            (str "Dieser Vorschlag basiert auf " (count parents) " weiteren Vorschlägen")
            (dd/list {:dense false}
              (map ui-parent parents))))

        (proposal-section "Meinungen"
          (vote-scale props))

        (proposal-section "Argumente"
          (layout/box {:mb 1}
            (if-not (empty? arguments)
              (dd/list {:dense true}
                (map ui-comment-row arguments))
              (dd/typography {:variant :body2 :color :textSecondary} "Bisher gibt es noch keine Argumente.")))
          (ui-new-comment-line {} {::proposal/id id}))))))