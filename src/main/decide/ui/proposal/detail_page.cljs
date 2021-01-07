(ns decide.ui.proposal.detail-page
  (:require
    [com.fulcrologic.fulcro.algorithms.merge :as mrg]
    [com.fulcrologic.fulcro.algorithms.normalized-state :as norm]
    [com.fulcrologic.fulcro.algorithms.react-interop :as interop]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [decide.models.argument :as argument]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]
    [material-ui.data-display :as dd]
    [material-ui.inputs :as inputs]
    [material-ui.layout :as layout]
    [material-ui.layout.grid :as grid]
    [material-ui.surfaces :as surfaces]
    ["@material-ui/icons/ArrowBack" :default ArrowBack]
    ["@material-ui/icons/CallSplit" :default CallSplit]
    ["@material-ui/icons/MergeType" :default MergeType]
    ["@material-ui/icons/ThumbUpAltTwoTone" :default ThumbUpAltTwoTone]
    ["@material-ui/icons/ThumbDownAltTwoTone" :default ThumbDownAltTwoTone]
    ["@material-ui/core/LinearProgress" :default LinearProgress]
    ["@material-ui/icons/Send" :default Send]
    ["@material-ui/core/styles" :refer [withStyles useTheme]]))

(declare ProposalPage)

(defn section [title & children]
  (apply layout/box {}
    (dd/typography {:variant "h6" :color "textPrimary" :component "h2"} title)
    children))

;; region Opinion section
(defn percent-of-pro-votes [pro-votes con-votes]
  (if (zero? pro-votes)
    0
    (* 100 (/ pro-votes (+ pro-votes con-votes)))))

(def vote-linear-progress
  (interop/react-factory
    ((withStyles
       (fn [theme]
         (clj->js {:barColorPrimary {:backgroundColor (.. theme -palette -success -main)}
                   :colorPrimary {:backgroundColor (.. theme -palette -error -main)}})))
     LinearProgress)))

(defsc OpinionSection [_ {::proposal/keys [pro-votes con-votes]
                          :or {pro-votes 0 con-votes 0}}]
  {:query [::proposal/id ::proposal/pro-votes ::proposal/con-votes]
   :ident ::proposal/id}
  (grid/container {:alignItems :center
                   :justify :space-between
                   :wrap :nowrap}
    (grid/item {:xs true :align :center} (str pro-votes))
    (grid/item {:xs 9}
      (vote-linear-progress
        {:variant :determinate
         :value (percent-of-pro-votes pro-votes con-votes)}))
    (grid/item {:xs true :align :center} (str con-votes))))

(def ui-opinion-section (comp/computed-factory OpinionSection {:keyfn ::proposal/id}))
;; endregion

;; region Argument section
(defsc Author [_ {::user/keys [display-name]}]
  {:query [::user/id ::user/display-name]
   :ident ::user/id}
  (dom/span display-name))

(def ui-argument-author (comp/factory Author))

(defsc ArgumentRow [_ {::argument/keys [content author]}]
  {:query [::argument/id ::argument/content {::argument/author (comp/get-query Author)}]
   :ident ::argument/id}
  (dd/list-item {}
    (dd/list-item-text {:primary   content
                        :secondary (comp/fragment "Von " (ui-argument-author author))})))

(def ui-argument-row (comp/computed-factory ArgumentRow {:keyfn ::argument/id}))

(defmutation add-argument [{::proposal/keys [id]
                            :keys [temp-id content]}]
  (action [{:keys [state]}]
    (let [new-argument-data {::argument/id temp-id
                             ::argument/content content}]
      (norm/swap!-> state
        (mrg/merge-component ArgumentRow new-argument-data
          :append (conj (comp/get-ident ProposalPage {::proposal/id id}) ::proposal/arguments)))))
  (remote [env]
    (-> env
      (m/with-server-side-mutation proposal/add-argument)
      (m/returning ArgumentRow))))

(defsc NewArgumentLine [this _ {::proposal/keys [id]}]
  {:use-hooks? true}
  (let [[new-argument set-new-argument] (hooks/use-state "")
        submit (hooks/use-callback
                 (fn [e]
                   (evt/prevent-default! e)
                   (comp/transact! this [(add-argument {::proposal/id id
                                                        :temp-id (tempid/tempid)
                                                        :content new-argument})])
                   (set-new-argument ""))
                 [new-argument])]
    (dom/form {:onSubmit submit}
      (inputs/textfield
        {:fullWidth true
         :label "Neues Argument"
         :variant :outlined
         :value new-argument
         :onChange #(set-new-argument (evt/target-value %))
         :inputProps {:aria-label "Neues Argument"}
         :InputProps {:endAdornment (inputs/icon-button {:type :submit
                                                         :aria-label "Absenden"}
                                      (comp/create-element Send nil nil))}}))))

(def ui-new-argument-line (comp/computed-factory NewArgumentLine))

(defsc ArgumentSection [_ {::proposal/keys [id arguments]}]
  {:query [::proposal/id
           {::proposal/arguments (comp/get-query ArgumentRow)}]
   :ident ::proposal/id}
  (comp/fragment
    (layout/box {:mb 1}
      (if-not (empty? arguments)
        (dd/list {:dense true}
          (map ui-argument-row arguments))
        (dd/typography {:variant :body2 :color :textSecondary} "Bisher gibt es noch keine Argumente.")))

    (ui-new-argument-line {} {::proposal/id id})))

(def ui-argument-section (comp/factory ArgumentSection {:keyfn ::proposal/id}))
;; endregion

;; region Parent section
(defsc Parent [_this {::proposal/keys [id nice-id title]}]
  {:query [::proposal/id ::proposal/nice-id ::proposal/title]
   :ident ::proposal/id}
  (dd/list-item
    {:button true
     :component "a"
     :href (str id)}
    (dd/list-item-avatar {} (dd/typography {:color "textSecondary"} (str "#" nice-id))) ; TODO buggy
    (dd/list-item-text {} (str title))))

(def ui-parent (comp/computed-factory Parent {:keyfn ::proposal/id}))

(defsc ParentSection [_ {::proposal/keys [parents]}]
  {:query [::proposal/id {::proposal/parents (comp/get-query Parent)}]
   :ident ::proposal/id}
  (when-not (empty? parents)
    (section
      (str "Dieser Vorschlag basiert auf " (count parents) " weiteren Vorschlägen")
      (dd/list {:dense true}
        (map ui-parent parents)))))

(def ui-parent-section (comp/factory ParentSection {:keyfn ::proposal/id}))
;; endregion

(defsc ProposalPage
  [_this {::proposal/keys [title body]
          :>/keys [parent-section argument-section opinion-section]}]
  {:query [::proposal/id
           ::proposal/title ::proposal/body
           {:>/parent-section (comp/get-query ParentSection)}
           {:>/opinion-section (comp/get-query OpinionSection)}
           {:>/argument-section (comp/get-query ArgumentSection)}]
   :ident ::proposal/id
   :use-hooks? true
   :route-segment ["decision" ::process/slug "proposal" ::proposal/id]
   :will-enter
   (fn will-enter-proposal-page
     [app {::proposal/keys [id]}]
     (let [ident (comp/get-ident ProposalPage {::proposal/id (uuid id)})]
       (dr/route-deferred ident
         #(df/load! app ident ProposalPage
            {:post-mutation `dr/target-ready
             :post-mutation-params {:target ident}}))))}
  (layout/container {}
    (surfaces/paper {}
      (layout/box {:p 2 :height "100vh"}
        (inputs/icon-button
          {:edge :start
           :color :inherit
           :aria-label "back"
           :onClick #(js/window.history.back)}
          (comp/create-element ArrowBack nil nil))
        (grid/container {:spacing 1}
          (grid/item {:xs 12}
            (dd/typography {:variant "h3" :component "h1" :gutterBottom true} title))
          (grid/item {:xs 12 :md 6}
            (section "Details" (dd/typography {:variant "body1"} body)))
          ;; "Dieser Vorschlag basiert auf " (count parents) " weiteren Vorschlägen"
          (grid/item {:xs 12 :md 6}
            (ui-parent-section parent-section))
          #_(grid/item {:xs 6}
              (section "Meinungen" (ui-opinion-section opinion-section)))
          (grid/item {:xs 12}
            (section "Argumente" (ui-argument-section argument-section))))))))

#_(inputs/button
    {:color :primary
     :variant :outlined
     :onClick #(comp/transact!! this [(new-proposal/show {:id slug
                                                          :parents [(comp/get-ident this)]})])
     :startIcon (layout/box {:clone true :css {:transform "rotate(.5turn)"}} (comp/create-element CallSplit nil nil))
     :endIcon (layout/box {:clone true :css {:transform "rotate(.5turn)"}} (comp/create-element MergeType nil nil))}
    "Fork / Merge")