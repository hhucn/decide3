(ns decide.ui.proposal.detail-page
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [decide.models.argumentation.ui :as argumentation.ui]
    [decide.models.opinion.api :as opinion.api]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.ui.proposal.new-proposal :as new-proposal]
    [decide.utils.breakpoint :as breakpoint]
    [mui.data-display :as dd]
    [mui.data-display.list :as list]
    [mui.inputs :as inputs]
    [mui.layout :as layout]
    [mui.layout.grid :as grid]
    [mui.surfaces :as surfaces]
    [mui.surfaces.card :as card]))

(declare ProposalPage)

(defn section [title & children]
  (apply layout/box {}
    (dd/typography {:variant "h4" :color "textPrimary" :component "h2" :paragraph true} title)
    children))

;; region Opinion section
(defsc OpinionSection [this {::proposal/keys [id pro-votes my-opinion-value]
                             :or {pro-votes 0
                                  my-opinion-value 0}}]
  {:query [::proposal/id
           ::proposal/pro-votes
           ::proposal/my-opinion-value]
   :ident ::proposal/id}
  (inputs/button
    {:variant (if (pos? my-opinion-value) :contained :outlined)
     :color :primary
     :onClick #(comp/transact! this [(opinion.api/add {::proposal/id id
                                                       :opinion (if (pos? my-opinion-value) 0 +1)})])}
    (if (pos? my-opinion-value)
      (i18n/tr "Approved")
      (i18n/tr "Approve"))))

(def ui-opinion-section (comp/computed-factory OpinionSection {:keyfn ::proposal/id}))
;; endregion


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

(defsc Relationship [_this {{::proposal/keys [id nice-id title]} :proposal}]
  {:query [{:proposal (comp/get-query Parent)}]}
  (list/item
    {:button true
     :component "a"
     :href (str id)}
    (list/item-avatar {} (dd/typography {:color "textSecondary"} (str "#" nice-id)))
    (list/item-text {} (str title))))

(def ui-relationship (comp/computed-factory Relationship {:keyfn (comp ::proposal/id :proposal)}))

(defsc ChildrenSection [_ {:keys [child-relations]}]
  {:query [::proposal/id
           {:child-relations (comp/get-query Relationship)}]
   :ident ::proposal/id}
  (grid/item {:xs 12 :component "section"}
    (section
      (i18n/trf "This proposal was derived {count, plural,  =1 {one time} other {# times}}" {:count (count child-relations)})
      (list/list {:dense false}
        (map ui-relationship child-relations)))))

(def ui-children-section (comp/factory ChildrenSection {:keyfn ::proposal/id}))

(defsc SmallRelationship [_this {{::proposal/keys [id nice-id title]} :proposal}]
  {:query [{:proposal (comp/get-query Parent)}]}
  (dd/chip
    {:component "a"
     :clickable true
     :href (str id)
     :label (str title)
     :variant :outlined
     :avatar
     (dd/avatar {:sx {:bgcolor "transparent"}}
       (str "#" nice-id))}))

(def ui-small-relationship (comp/computed-factory SmallRelationship {:keyfn (comp ::proposal/id :proposal)}))

(defsc SmallParentSection [_ {:keys [parent-relations]}]
  {:query [::proposal/id
           {:parent-relations (comp/get-query Relationship)}]
   :ident ::proposal/id}
  (when-not (empty? parent-relations)
    (dd/typography {:component :label
                    :variant :caption}
      (i18n/trf "This proposal {count, plural, =0 {depends on no previous proposals} =1 {is derived from one proposal} other {combines # proposals}}" {:count (count parent-relations)})
      (grid/container
        {:spacing 1
         :style {:marginTop "4px"
                 :listStyle "none"
                 :paddingLeft "0"}
         :component :ul}
        (for [relation parent-relations
              :let [entry (ui-small-relationship relation)]]
          (grid/item {:key (.-key entry)
                      :component :li}
            entry))))))

(def ui-small-parent-section (comp/computed-factory SmallParentSection))

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
    (dom/svg {:classes ["venn"] :height "100%" :viewBox viewbox :fill "currentColor" :stroke "currentColor" :aria-hidden true}
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

(defsc SimilarEntry [this {:keys [sum-uniques own-uniques common-uniques other-proposal other-uniques] :as props}
                     {:keys [show-add-dialog process-over?]}]
  {:query [:own-uniques

           :common-uniques
           :sum-uniques

           {:other-proposal (comp/get-query SimilarProposal)}
           :other-uniques]}
  (let [others-total (+ common-uniques other-uniques)
        own-total (+ own-uniques common-uniques)]
    (card/card {:variant :outlined :style {:flexGrow 1}}
      (card/action-area {:href (str (::proposal/id other-proposal))}
        (card/header {:title (ui-similar-proposal other-proposal)})
        (card/content {}
          (grid/container {:spacing 1}

            (grid/item {:xs true}
              (dd/typography {:variant :caption :component :label} (i18n/trc "Overlap of voters" "Overlap")
                (dd/typography {:color "inherit"}
                  (i18n/trf "{ratioOfVoters, number, ::percent} of approvees" {:ratioOfVoters (/ common-uniques sum-uniques)}))))

            (grid/item {:xs true}
              (dd/typography {:variant :caption :component :label} (i18n/tr "Potential approves")
                (dd/typography {:sx {:color "success.main"}}
                  (i18n/trf "{ratioOfVoters, number, ::+! percent} more approves" {:ratioOfVoters (dec (/ sum-uniques own-total))})))))))

      (when-not process-over?
        (card/actions {}
          (inputs/button
            {:color :secondary
             :onClick #(show-add-dialog (comp/get-ident SimilarProposal other-proposal))}
            (i18n/trc "Encourage to form a coalition" "Form coalition")))))))


(def ui-similarity-entry (comp/computed-factory SimilarEntry {:keyfn (comp ::proposal/id :other-proposal)}))

(defsc SimilarSection [_ {:keys [similar]} {:keys [show-add-dialog
                                                   process-over?]}]
  {:query [::proposal/id
           {:similar (comp/get-query SimilarEntry)}]
   :ident ::proposal/id}
  (list/list
    {:dense true
     :disablePadding true}
    (for [sim similar
          :let [entry (ui-similarity-entry sim {:show-add-dialog show-add-dialog
                                                :process-over? process-over?})]]
      (list/item
        {:key (.-key entry)
         :disableGutters true}
        entry))))


(def ui-similar-section (comp/computed-factory SimilarSection))
;; endregion

(defsc Process [_ _]
  {:query [::process/slug ::process/end-time]
   :ident ::process/slug})

(declare ProposalPage)

(defmutation init-proposal-page [_]
  (action [{:keys [app ref]}]
    (df/load! app ref ProposalPage
      {:without #{::proposal/positions}
       :post-mutation `dr/target-ready
       :post-mutation-params {:target ref}})
    (df/load! app ref ProposalPage
      {:focus [:>/argumentation-section]
       :without #{:argument/premise->arguments}
       :marker [:load-argument-section ref]})))


(defsc ProposalPage
  [this {::proposal/keys [title body]
         :keys [ui/current-process]
         :>/keys [parent-section children-section opinion-section similar-section argumentation-section]}]
  {:query [::proposal/id
           ::proposal/title ::proposal/body
           {:>/children-section (comp/get-query ChildrenSection)}
           {:>/parent-section (comp/get-query SmallParentSection)}
           {:>/opinion-section (comp/get-query OpinionSection)}
           {:>/similar-section (comp/get-query SimilarSection)}
           {:>/argumentation-section (comp/get-query argumentation.ui/ArgumentList)}
           {[:ui/current-process '_] (comp/get-query Process)}]
   :ident ::proposal/id
   :use-hooks? true
   :route-segment ["proposal" ::proposal/id]
   :will-enter
   (fn will-enter-proposal-page
     [app {::proposal/keys [id]}]
     (let [ident (comp/get-ident ProposalPage {::proposal/id (uuid id)})]
       (dr/route-deferred ident
         #(comp/transact! app [(init-proposal-page {})]
            {:ref ident :only-refresh [ident]}))))}
  (let [{::process/keys [slug] :as process} current-process
        logged-in? (comp/shared this :logged-in?)
        process-over? (process/over? process)
        has-children? (seq (:child-relations children-section))
        show-similar-section? (and (process/single-approve? process)
                                (seq (:similar similar-section)))
        show-right-side? (or has-children? show-similar-section?)
        show-add-dialog (hooks/use-callback
                          (fn [& idents]
                            (comp/transact! this
                              [(new-proposal/show
                                 {:slug slug
                                  :parents (apply vector (comp/get-ident this) idents)})]))
                          [slug])]
    (layout/container {:maxWidth :xl :disableGutters (breakpoint/<=? "sm")}
      (surfaces/paper {:sx {:mt 2 :p 2}}
        (grid/container {:spacing 2 :component "main"}
          (grid/item {:xs 12}
            (dd/typography {:variant "h3" :component "h1"} title)
            ;; " Dieser Vorschlag basiert auf " (count parents) " weiteren Vorschlägen "
            (ui-small-parent-section parent-section))

          ;; Left side
          (grid/container {:item true :xs 12 :lg (if show-right-side? 8 12)
                           :alignContent "flex-start"
                           :spacing 1}

            (grid/item {:xs 12 :component "section"}
              (dd/typography {:variant "body1" :style {:whiteSpace "pre-line"}} body))

            (grid/item {:xs 12}
              (surfaces/toolbar
                {:variant :dense
                 :disableGutters true}
                (when-not process-over?
                  (inputs/button
                    {:color :primary
                     :disabled (not logged-in?)
                     :variant :contained
                     :size :small
                     :onClick #(comp/transact! this [(new-proposal/show {:parents [(comp/get-ident this)]})])}
                    (i18n/trc "Prompt to merge or fork" "Propose a change")))))


            (grid/item {:xs 6}
              (ui-opinion-section opinion-section))
            (grid/item {:xs 12 :component "section"}
              (section (i18n/tr "Argumentation")
                (argumentation.ui/ui-argument-list argumentation-section))))

          ;; Right side
          (when-not show-right-side?
            (grid/item {:xs 12 :lg 4 :component "section"}
              (when has-children?
                (ui-children-section children-section))
              (when show-similar-section?
                (section (i18n/tr "Possible coalitions")
                  (ui-similar-section similar-section {:show-add-dialog show-add-dialog
                                                       :process-over? process-over?}))))))))))