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
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]
    [goog.string :as gstring]
    [material-ui.data-display :as dd]
    [material-ui.data-display.list :as list]
    [material-ui.data-display.table :as table]
    [material-ui.inputs :as inputs]
    [material-ui.layout :as layout]
    [material-ui.layout.grid :as grid]
    [material-ui.surfaces :as surfaces]
    ["@material-ui/icons/CallSplit" :default CallSplit]
    ["@material-ui/icons/MergeType" :default MergeType]
    ["@material-ui/icons/ThumbUpAltTwoTone" :default ThumbUpAltTwoTone]
    ["@material-ui/icons/ThumbDownAltTwoTone" :default ThumbDownAltTwoTone]
    ["@material-ui/core/LinearProgress" :default LinearProgress]
    ["@material-ui/icons/Send" :default Send]
    ["@material-ui/core/styles" :refer [withStyles useTheme]]
    [decide.ui.proposal.new-proposal :as new-proposal]))

(declare ProposalPage)
(defn merge-icon [] (comp/create-element MergeType #js {:style #js {:transform "rotate (0.5turn)"}} nil))

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

(defsc ArgumentRow [_ {::argument/keys [content author type]}]
  {:query [::argument/id ::argument/type ::argument/content {::argument/author (comp/get-query Author)}]
   :ident ::argument/id}
  (list/item {}
    (list/item-icon {}
      (case type
        :pro (layout/box {:color "success.main"} "Pro:")
        :contra (layout/box {:color "error.main"} "Con:")))
    (list/item-text {:primary content
                     :secondary (comp/fragment "Von " (ui-argument-author author))})))

(def ui-argument-row (comp/computed-factory ArgumentRow {:keyfn ::argument/id}))

(defmutation add-argument [{::proposal/keys [id]
                            :keys [temp-id content type author-id]}]
  (action [{:keys [state]}]
    (let [new-argument-data {::argument/id temp-id
                             ::argument/content content
                             ::argument/type type
                             ::argument/author {::user/id author-id}}]
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
        [type set-type] (hooks/use-state :pro)
        toggle-type (hooks/use-callback #(set-type ({:pro :contra
                                                     :contra :pro} type)) [type])
        submit (hooks/use-callback
                 (fn [e]
                   (evt/prevent-default! e)
                   (comp/transact! this [(add-argument {::proposal/id id
                                                        :temp-id (tempid/tempid)
                                                        :content new-argument
                                                        :type type})])
                   (set-new-argument ""))
                 [new-argument type])]
    (dom/form {:onSubmit submit}
      (inputs/textfield
        {:fullWidth true
         :label "Neues Argument"
         :variant :outlined
         :value new-argument
         :onChange #(set-new-argument (evt/target-value %))
         :inputProps {:aria-label "Neues Argument"}
         :InputProps {:startAdornment
                      (comp/fragment
                        (layout/box {:color "success.main" :display (when (not= :pro type) "none")}
                          (inputs/button {:color :inherit :onClick toggle-type} "Dafür"))
                        (layout/box {:color "error.main" :display (when (not= :contra type) "none")}
                          (inputs/button {:color :inherit :onClick toggle-type} "Dagegen")))
                      :endAdornment (inputs/icon-button {:type :submit
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
        (list/list {:dense true}
          (map ui-argument-row arguments))
        (dd/typography {:variant :body2 :color :textSecondary} "Bisher gibt es noch keine Argumente.")))

    (ui-new-argument-line {} {::proposal/id id})))

(def ui-argument-section (comp/factory ArgumentSection {:keyfn ::proposal/id}))
;; endregion

;; region Parent section
(defsc Parent [_this {::proposal/keys [id nice-id title]}]
  {:query [::proposal/id ::proposal/nice-id ::proposal/title]
   :ident ::proposal/id}
  (list/item
    {:button true
     :component "a"
     :href (str id)}
    (list/item-avatar {} (dd/typography {:color "textSecondary"} (str "#" nice-id)))
    (list/item-text {} (str title))))

(def ui-parent (comp/computed-factory Parent {:keyfn ::proposal/id}))

(defsc ParentSection [_ {::proposal/keys [parents children]}]
  {:query [::proposal/id
           {::proposal/parents (comp/get-query Parent)}
           {::proposal/children (comp/get-query Parent)}]
   :ident ::proposal/id}
  (when-not (and (empty? parents) (empty? children))
    (grid/item {:xs 12 :md 6 :component "section"}

      (when-not (empty? parents)
        (section
          (str "Dieser Vorschlag basiert auf " (count parents) " weiteren Vorschlägen.")
          (list/list {:dense false}
            (map ui-parent parents))))

      (when-not (empty? children)
        (section
          (str "Dieser Vorschlag hat " (count children) " Abkömmling" (when (<= 2 (count children)) "e") ". ")
          (list/list {:dense false}
            (map ui-parent children)))))))

(def ui-parent-section (comp/factory ParentSection {:keyfn ::proposal/id}))
;; endregion

;; region Similar section
(defsc SimilarProposal [_ {::proposal/keys [title]}]
  {:query [::proposal/id ::proposal/title]
   :ident ::proposal/id}
  (dom/span (str title)))
(def ui-similar-proposal (comp/computed-factory SimilarProposal {:keyfn ::proposal/id}))

(defn venn-diagramm [{:keys [own-uniques common-uniques other-uniques]}]
  (let [radius 40
        y-circle radius
        x-left-circle radius
        x-right-circle (+ x-left-circle radius (/ radius -6))
        viewbox (str "-5 -5 " (+ x-right-circle radius 10) " " (+ y-circle radius 10))]
    (dom/svg {:classes ["venn"] :height "50%" :viewBox viewbox :fill "currentColor" :stroke "currentColor"}
      (dom/circle {:cx x-left-circle :cy y-circle :r radius :style {:fill "none"}})
      (dom/circle {:cx x-right-circle :cy y-circle :r radius :style {:fill "none"}})
      (dom/text
        {:x (- x-left-circle (/ radius 2)) :y y-circle
         :textAnchor "middle" :dominantBaseline "central"}
        (str own-uniques))
      (dom/text {:x (/ (+ x-right-circle x-left-circle) 2) :y y-circle
                 :textAnchor "middle" :dominantBaseline "central"}
        (str common-uniques))
      (dom/text
        {:x (+ x-right-circle (/ radius 2)) :y y-circle
         :textAnchor "middle" :dominantBaseline "central"}
        (str other-uniques)))))

(defsc SimilarEntry [this {:keys [own-proposal sum-uniques own-uniques common-uniques other-proposal other-uniques] :as props} {:keys [show-add-dialog]}]
  {:query [{:own-proposal (comp/get-query SimilarProposal)}
           :own-uniques

           :common-uniques
           :sum-uniques

           {:other-proposal (comp/get-query SimilarProposal)}
           :other-uniques]}
  (let [others-total (+ common-uniques other-uniques)
        own-total (+ own-uniques common-uniques)]
    (table/row {}
      (table/cell {} (ui-similar-proposal other-proposal))
      (table/cell {} (layout/box {}
                       (dd/typography {:color "inherit"}
                         (let [value (* (/ common-uniques sum-uniques) 100)]
                           (gstring/format "%.0f%" value)))))
      (table/cell {} (layout/box {:clone true :color "success.main"} (dd/typography {} (gstring/format "+ %.0f%" (- (* (/ sum-uniques own-total) 100) 100)))))
      (table/cell {}
        (inputs/button
          {:variant :outlined
           :size :small
           :color :primary
           :onClick #(show-add-dialog (comp/get-ident SimilarProposal other-proposal))}
          "Merge"))
      (table/cell {:align :center} (venn-diagramm props)))))

(def ui-similarity-entry (comp/computed-factory SimilarEntry {:keyfn (comp ::proposal/id :other-proposal)}))

(defsc SimilarSection [_ {:keys [similar]} {:keys [show-add-dialog]}]
  {:query [::proposal/id
           {:similar (comp/get-query SimilarEntry)}]
   :ident ::proposal/id}
  (table/table {:size :small}
    (table/head {}
      (table/row {}
        (table/cell {} "Vorschlag")
        (table/cell {} "Übereinstimmung")
        (table/cell {} "Potentielle Stimmen")
        (table/cell {})
        (table/cell {} "Stimmen (eig./zsm./fremd)")))

    (table/body {}
      (map #(ui-similarity-entry % {:show-add-dialog show-add-dialog}) (sort-by (fn [{:keys [sum-uniques common-uniques]}] (/ common-uniques sum-uniques)) > similar)))))

(def ui-similar-section (comp/computed-factory SimilarSection))
;; endregion

(defsc ProposalPage
  [this {::proposal/keys [title body]
         :>/keys [parent-section argument-section opinion-section similar-section]}
   {:keys [slug]}]
  {:query [::proposal/id
           ::proposal/title ::proposal/body
           {:>/parent-section (comp/get-query ParentSection)}
           {:>/opinion-section (comp/get-query OpinionSection)}
           {:>/argument-section (comp/get-query ArgumentSection)}
           {:>/similar-section (comp/get-query SimilarSection)}]
   :ident ::proposal/id
   :use-hooks? true
   :route-segment ["proposal" ::proposal/id]
   :will-enter
   (fn will-enter-proposal-page
     [app {::proposal/keys [id]}]
     (let [ident (comp/get-ident ProposalPage {::proposal/id (uuid id)})]
       (dr/route-deferred ident
         #(df/load! app ident ProposalPage
            {:post-mutation `dr/target-ready
             :post-mutation-params {:target ident}}))))}
  (let [show-add-dialog (hooks/use-callback
                          (fn [& idents] (comp/transact!! this [(new-proposal/show {:id slug
                                                                                    :parents (apply vector (comp/get-ident this) idents)})]))
                          [slug])]
    (layout/container {}
      (layout/box {:p 2 :clone true}
        (surfaces/paper {:p 2}
          (grid/container {:spacing 3 :component "main"}
            (grid/item {:xs 12}
              (dd/typography {:variant "h3" :component "h1"} title))
            (grid/item {:xs 12}
              (surfaces/toolbar {:disableGutters true :variant :dense}
                (inputs/button
                  {:color :primary
                   :variant :outlined
                   :onClick #(comp/transact!! this [(new-proposal/show {:id slug
                                                                        :parents [(comp/get-ident this)]})])
                   :startIcon (layout/box {:clone true :css {:transform "rotate (.5turn)"}} (comp/create-element CallSplit nil nil))
                   :endIcon (layout/box {:clone true :css {:transform "rotate (.5turn)"}} (comp/create-element MergeType nil nil))}
                  " Fork / Merge ")))
            (grid/item {:xs true :component "section"}
              (section " Details " (dd/typography {:variant "body1"} body)))

            ;; " Dieser Vorschlag basiert auf " (count parents) " weiteren Vorschlägen "
            (ui-parent-section parent-section)
            #_(grid/item {:xs 6}
                (section " Meinungen " (ui-opinion-section opinion-section)))
            (grid/item {:xs 12 :component "section"}
              (section "Argumente" (ui-argument-section argument-section)))
            (grid/item {:xs 12 :component "section"}
              (section "Ähnliche Vorschläge" (ui-similar-section similar-section {:show-add-dialog show-add-dialog})))))))))