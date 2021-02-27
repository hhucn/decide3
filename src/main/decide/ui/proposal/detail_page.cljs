(ns decide.ui.proposal.detail-page
  (:require
    [clojure.string :as str]
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
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
    [decide.models.authorization :as auth]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]
    [material-ui.data-display :as dd]
    [material-ui.data-display.list :as list]
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
    ["@material-ui/icons/DoneAll" :default DoneAllIcon]
    ["@material-ui/core/styles" :refer [withStyles useTheme]]
    [decide.ui.proposal.new-proposal :as new-proposal]
    [decide.models.process :as process]))

(declare ProposalPage)

(defn section [title & children]
  (apply layout/box {}
    (dd/typography {:variant "h4" :color "textPrimary" :component "h2" :paragraph true} title)
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
        :pro (layout/box {:color "success.main"} (i18n/tr "Pro"))
        :contra (layout/box {:color "error.main"} (i18n/tr "Con"))))
    (list/item-text {:primary content
                     :secondary (comp/fragment (i18n/tr "By ") (ui-argument-author author))})))

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

(defn type-toggle [{:keys [color type selected-type label] :as props}]
  (layout/box {:color color :display (when (not= selected-type type) "none")}
    (inputs/button (merge props {:color :inherit :type :button}) label)))

(defn submit-new-argument-button [props]
  (inputs/icon-button
    (merge props
      {:type :submit
       :aria-label (i18n/trc "Submit form" "Submit")})
    (dom/create-element Send)))

(defsc NewArgumentLine [_ {:keys []} {:keys [root/current-session add-argument!]}]
  {:use-hooks? true}
  (let [user-id (get-in current-session [:user ::user/id])
        logged-in? (get current-session :session/valid?)
        [new-argument set-new-argument!] (hooks/use-state "")
        [type set-type!] (hooks/use-state :pro)
        toggle-type! (hooks/use-callback #(set-type! ({:pro :contra
                                                       :contra :pro} type)) [type])
        submit (hooks/use-callback
                 (fn [e]
                   (evt/prevent-default! e)
                   (when-not (str/blank? new-argument)
                     (add-argument! {::argument/content new-argument
                                     ::argument/type type
                                     :author-id user-id})
                     (set-new-argument! "")))
                 [new-argument type])]
    (dom/form {:onSubmit submit
               :disabled (not logged-in?)}
      (inputs/textfield
        {:fullWidth true
         :label (i18n/tr "New argument")
         :variant :outlined
         :value new-argument
         :onChange #(set-new-argument! (evt/target-value %))
         :disabled (not logged-in?)
         :placeholder (when (not logged-in?) (i18n/tr "Login to add new argument"))
         :inputProps {:aria-label (i18n/tr "New argument")}
         :InputProps {:startAdornment
                      (comp/fragment
                        (type-toggle
                          {:label (i18n/tr "Pro")
                           :type :pro
                           :color "success.main"
                           :onClick toggle-type!
                           :disabled (not logged-in?)
                           :selected-type type})
                        (type-toggle
                          {:label (i18n/tr "Contra")
                           :type :contra
                           :color "error.main"
                           :onClick toggle-type!
                           :disabled (not logged-in?)
                           :selected-type type}))
                      :endAdornment (submit-new-argument-button {:disabled (not logged-in?)})}}))))


(def ui-new-argument-line (comp/computed-factory NewArgumentLine))

(defsc ArgumentSection [this {::proposal/keys [id arguments]
                              :keys [root/current-session]}
                        {:keys [process-over?]}]
  {:query [::proposal/id
           {::proposal/arguments (comp/get-query ArgumentRow)}
           {[:root/current-session '_] (comp/get-query auth/Session)}]
   :ident ::proposal/id}
  (comp/fragment
    (layout/box {:mb 1}
      (if-not (empty? arguments)
        (list/list {:dense true}
          (map ui-argument-row arguments))
        (dd/typography
          {:variant :body1
           :color :textSecondary
           :paragraph true}
          (i18n/tr "So far there are no arguments"))))

    (when-not process-over?
      (ui-new-argument-line {}
        {:root/current-session current-session
         :add-argument! (fn [{::argument/keys [content type]
                              :keys [user-id]}]
                          (comp/transact! this
                            [(add-argument {::proposal/id id
                                            :temp-id (tempid/tempid)
                                            :content content
                                            :type type
                                            :author-id user-id})]))}))))

(def ui-argument-section (comp/computed-factory ArgumentSection {:keyfn ::proposal/id}))
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
  (when-not (empty? child-relations)
    (grid/item {:xs 12 :component "section"}
      (section
        (i18n/trf "This proposal was derived {count, plural,  =1 {one time} other {# times}}" {:count (count child-relations)})
        (list/list {:dense false}
          (map ui-relationship child-relations))))))

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
     (layout/box {:clone true :bgcolor "transparent"}
       (dd/avatar {}
         (str "#" nice-id)))}))

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
    (surfaces/card {:variant :outlined :style {:flexGrow 1}}
      (surfaces/card-action-area {:href (str (::proposal/id other-proposal))}
        (surfaces/card-header {:title (ui-similar-proposal other-proposal)})
        (surfaces/card-content {}
          (grid/container {:spacing 1}

            (grid/item {:xs true}
              (dd/typography {:variant :caption :component :label} "Übereinstimmung"
                (dd/typography {:color "inherit"}
                  (i18n/trf "{ratioOfVoters, number, ::percent} of approvees" {:ratioOfVoters (/ common-uniques sum-uniques)}))))

            (grid/item {:xs true}
              (dd/typography {:variant :caption :component :label} "Potentielle Stimmen"
                (layout/box {:clone true :color "success.main"}
                  (dd/typography {}
                    (i18n/trf "{ratioOfVoters, number, ::+! percent} more approves" {:ratioOfVoters (dec (/ sum-uniques own-total))}))))))))

      (when-not process-over?
        (surfaces/card-actions {}
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

(defsc ProposalPage
  [this {::proposal/keys [title body]
         :keys [ui/current-process]
         :>/keys [parent-section children-section argument-section opinion-section similar-section]}]
  {:query [::proposal/id
           ::proposal/title ::proposal/body
           {:>/children-section (comp/get-query ChildrenSection)}
           {:>/parent-section (comp/get-query SmallParentSection)}
           {:>/opinion-section (comp/get-query OpinionSection)}
           {:>/argument-section (comp/get-query ArgumentSection)}
           {:>/similar-section (comp/get-query SimilarSection)}
           {[:ui/current-process '_] (comp/get-query Process)}]
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
  (let [{::process/keys [slug] :as process} current-process
        process-over? (process/over? process)
        show-add-dialog (hooks/use-callback
                          (fn [& idents]
                            (comp/transact! this
                              [(new-proposal/show
                                 {:slug slug
                                  :parents (apply vector (comp/get-ident this) idents)})]))
                          [slug])]
    (layout/container {:maxWidth :xl}
      (layout/box {:my 2 :p 2 :clone true}
        (surfaces/paper {}
          (grid/container {:spacing 2 :component "main"}
            (grid/item {:xs 12}
              (dd/typography {:variant "h3" :component "h1"} title)
              ;; " Dieser Vorschlag basiert auf " (count parents) " weiteren Vorschlägen "
              (ui-small-parent-section parent-section))

            (grid/container {:item true :xs 12 :lg 8
                             :alignContent "flex-start"
                             :spacing 1}

              (grid/item {:xs 12 :component "section"}
                (section (i18n/trc "Details of a proposal" "Details") (dd/typography {:variant "body1" :style {:whiteSpace "pre-line"}} body)))

              (grid/item {:xs 12}
                (surfaces/toolbar
                  {:variant :dense
                   :disableGutters true}
                  (when-not process-over?
                    (inputs/button
                      {:color :primary
                       :variant :contained
                       :size :small
                       :onClick #(comp/transact! this [(new-proposal/show {:parents [(comp/get-ident this)]})])}
                      ;:startIcon (layout/box {:css {:transform "rotate(.5turn)"} :component MergeType})
                      ;:endIcon (layout/box {:css {:transform "rotate(.5turn)"} :component CallSplit})}
                      (i18n/trc "Prompt to merge or fork" "Propose a change")))))


              #_(grid/item {:xs 6}
                  (section " Meinungen " (ui-opinion-section opinion-section)))
              (grid/item {:xs 12 :component "section"}
                (section (i18n/trc "Arguments of a proposal" "Arguments") (ui-argument-section argument-section
                                                                            {:process-over? process-over?}))))
            (grid/item {:xs 12 :lg 4 :component "section"}
              (ui-children-section children-section)
              (section (i18n/tr "Similar proposals") (ui-similar-section similar-section {:show-add-dialog show-add-dialog
                                                                                          :process-over? process-over?})))))))))