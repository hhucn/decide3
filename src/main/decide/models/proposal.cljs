(ns decide.models.proposal
  (:require
    [clojure.string :as str]
    [com.fulcrologic.fulcro.algorithms.merge :as mrg]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro.algorithms.normalized-state :as norm-state]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li p h1 h3 form button input span]]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro-css.css :as css]
    [decide.routing :as routing]
    [decide.ui.components.breadcrumbs :as breadcrumbs]
    [decide.ui.themes :as themes]
    [material-ui.data-display :as dd]
    [material-ui.feedback :as feedback]
    [material-ui.surfaces :as surfaces]
    [material-ui.inputs :as input]
    [material-ui.navigation :as navigation]
    [material-ui.layout :as layout]
    [com.fulcrologic.fulcro.algorithms.react-interop :as interop]
    ["@material-ui/icons/ThumbUpAlt" :default ThumbUpAlt]
    ["@material-ui/icons/ThumbDownAlt" :default ThumbDownAlt]
    ["@material-ui/core/LinearProgress" :default LinearProgress]
    ["@material-ui/core/styles" :refer (withStyles)]
    ["react" :as React]
    [taoensso.timbre :as log]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.data-fetch :as df]))


(defsc Proposal [this {:proposal/keys [id title body]}]
  {:query         (fn []
                    [:proposal/id :proposal/title :proposal/body
                     :proposal/pro-votes :proposal/con-votes
                     {:proposal/parents '...}])
   :ident         :proposal/id
   :initial-state (fn [{:keys [id title body]}]
                    #:proposal{:id        id
                               :title     title
                               :body      body
                               :pro-votes 534
                               :con-votes 340})}
  (surfaces/card {:style {:width "100%"}}
    (surfaces/card-action-area
      {:href (routing/path->url
               (dr/path-to
                 (comp/registry-key->class 'decide.ui.main-app/MainApp)
                 (comp/registry-key->class `ProposalPage)
                 id))}
      (surfaces/card-content {}
        (dd/typography {:gutterBottom true :variant "h6" :component "h2"}
          (dd/typography
            {:variant "subtitle1" :component "span" :color "textSecondary"
             :style   {:marginRight ".25em"}}
            (str "#" (if (tempid/tempid? id) "?" id)))
          title)
        (dd/typography {:variant "body2" :color "textSecondary" :component "p"} body)))
    (surfaces/card-actions {}
      (input/button {:size "small" :color "primary" :startIcon (React/createElement ThumbUpAlt)} "Zustimmen")
      (input/button {:size "small" :color "primary" :startIcon (React/createElement ThumbDownAlt)} "Ablehnen"))))

(def ui-proposal (comp/computed-factory Proposal {:keyfn :proposal/id}))

(defn href-to-proposal-list []
  (routing/path->url
    (dr/path-to
      (comp/registry-key->class 'decide.ui.main-app/MainApp)
      (comp/registry-key->class 'decide.ui.main-app/MainProposalList))))

(defn percent-of-pro-votes [pro-votes con-votes]
  (* 100 (/ pro-votes (+ pro-votes con-votes))))

(def vote-linear-progress
  (interop/react-factory
    ((withStyles
       (fn [theme]
         (clj->js {:barColorPrimary {:backgroundColor (-> theme .-palette .-success .-main)}
                   :colorPrimary {:backgroundColor (-> theme .-palette .-error .-main)}})))
     LinearProgress)))

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

(defn vote-scale [{:proposal/keys [pro-votes con-votes] :or {pro-votes 0 con-votes 0}}]
  (layout/grid {:container  true
                :alignItems :center
                :justify    :space-between}
    (layout/grid {:item true :xs 1 :align :center} pro-votes)
    (layout/grid {:item true :xs 9}
      (vote-linear-progress
        {:variant "determinate"
         :value   (percent-of-pro-votes pro-votes con-votes)}))
    (layout/grid {:item true :xs 1 :align :center} con-votes)))

(defn proposal-section [title & children]
  (comp/fragment
    (dd/divider {:light true})
    (apply layout/box {:mt 1 :mb 2}
      (dd/typography {:variant "h6" :color "textSecondary" :component "h3"} title)
      children)))

(defsc ProposalPage [this {:proposal/keys [id title body parents pro-votes con-votes] :as props}]
  {:query         [:proposal/id :proposal/title :proposal/body
                   {:proposal/parents (comp/get-query Parent)}
                   :proposal/pro-votes :proposal/con-votes]
   :ident         :proposal/id
   :route-segment ["proposal" :proposal-id]
   :will-enter    (fn [app {:keys [proposal-id]}]
                    (dr/route-deferred [:proposal/id proposal-id]
                      #(df/load! app [:proposal/id proposal-id] ProposalPage
                         {:post-mutation        `dr/target-ready
                          :post-mutation-params {:target (comp/get-ident ProposalPage {:proposal/id proposal-id})}})))}
  (log/info "Proposal Page" props)
  (layout/container {:maxWidth :lg}
    (breadcrumbs/breadcrumb-nav
      [["Vorschläge" (href-to-proposal-list)]
       [(str "#" id) ""]])
    (surfaces/card {}
      (surfaces/card-content {}
        (dd/typography {:variant "h5" :component "h2" :gutterBottom true}
          title
          (dd/typography {:variant   "subtitle1"
                          :component "span"
                          :color     "textSecondary"
                          :style     {:marginLeft ".3em"}}
            (str "#" id)))

        (dd/typography {:variant   "body2"
                        :paragraph true
                        :color     "textSecondary"
                        :component "p"}
          body)

        (when-not (empty? parents)
          (proposal-section "Dieser Vorschlag basiert auf"
            (dd/list {:dense true}
              (map ui-parent parents))))

        (proposal-section "Meinungen"
          (vote-scale props))

        (proposal-section "Argumente")))))
