(ns decide.ui.proposal.page
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [decide.routing :as routing]
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
    ["@material-ui/core/LinearProgress" :default LinearProgress]
    ["@material-ui/core/styles" :refer (withStyles useTheme)]
    ["React" :as react]
    [com.fulcrologic.fulcro.dom :as dom]
    [taoensso.timbre :as log]))

(defsc Parent [this {:proposal/keys [id title]}]
  {:query [:proposal/id :proposal/title]
   :ident :proposal/id}
  (dd/list-item
    {:button    true
     :component "a"
     :href      (routing/path->url
                  (dr/path-to
                    (comp/registry-key->class 'decide.ui.main-app/MainApp)
                    (comp/registry-key->class `ProposalPage)
                    id))}
    (dd/list-item-avatar {} (str "#" id))
    (dd/list-item-text {} (str title))))

(def ui-parent (comp/computed-factory Parent {:keyfn :proposal/id}))

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

(defn vote-scale [{:proposal/keys [pro-votes con-votes]
                   :or            {pro-votes 0 con-votes 0}}]
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

(defsc ProposalPage [this {:proposal/keys [id title body parents original-author] :as props}]
  {:query         [:proposal/id :proposal/title :proposal/body
                   {:proposal/parents (comp/get-query Parent)}
                   :proposal/pro-votes :proposal/con-votes
                   {:proposal/original-author [:profile/name]}]
   :ident         :proposal/id
   :route-segment ["proposal" :proposal-id]
   :will-enter    (fn will-enter-proposal-page
                    [app {:keys [proposal-id]}]
                    (dr/route-deferred [:proposal/id proposal-id]
                      #(df/load! app [:proposal/id proposal-id] ProposalPage
                         {:post-mutation        `dr/target-ready
                          :post-mutation-params {:target (comp/get-ident ProposalPage {:proposal/id proposal-id})}})))
   :use-hooks?    true}
  (feedback/dialog
    {:open       true
     :fullScreen (utils/<=-breakpoint? "xs")
     :fullWidth  true
     :maxWidth   "md"
     :onClose    #(js/window.history.back)}

    (surfaces/toolbar {:variant "dense"}
      (inputs/icon-button
        {:edge       :start
         :color      :inherit
         :aria-label "back"
         :onClick    #(js/window.history.back)}
        (react/createElement Close))
      (feedback/dialog-title {} title))
    (feedback/dialog-content {}
      (dd/typography {:variant   "body1"
                      :paragraph true}
        body)

      (when-not (empty? parents)
        (proposal-section
          (str "Dieser Vorschlag basiert auf " (count parents) " weiteren Vorschlägen")
          (dd/list {:dense false}
            (map ui-parent parents))))

      (proposal-section "Meinungen"
        (vote-scale props))

      (proposal-section "Argumente"))))