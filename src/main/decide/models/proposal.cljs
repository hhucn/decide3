(ns decide.models.proposal
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li p h1 h3 form button input span]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [decide.routing :as routing]
    [decide.ui.components.breadcrumbs :as breadcrumbs]
    [material-ui.data-display :as dd]
    [material-ui.surfaces :as surfaces]
    [material-ui.inputs :as input]
    [material-ui.layout :as layout]
    [com.fulcrologic.fulcro.algorithms.react-interop :as interop]
    ["@material-ui/icons/ThumbUpAlt" :default ThumbUpAlt]
    ["@material-ui/icons/ThumbUpAltTwoTone" :default ThumbUpAltTwoTone]
    ["@material-ui/icons/ThumbDownAltTwoTone" :default ThumbDownAltTwoTone]
    ["@material-ui/icons/MoreVert" :default MoreVertIcon]
    ["@material-ui/core/LinearProgress" :default LinearProgress]
    ["@material-ui/core/styles" :refer (withStyles useTheme)]
    ["react" :as React]
    [taoensso.timbre :as log]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.data-fetch :as df]))

(defmutation add-opinion [{:keys [proposal/id opinion]}]
  (action [{:keys [state]}]
    (swap! state update-in [:proposal/id id] assoc :proposal/opinion opinion))
  (remote [env]
    (m/with-server-side-mutation env 'decide.api.proposal/add-opinion)))

(defsc Proposal [this {:proposal/keys [id title body opinion]}]
  {:query         (fn []
                    [:proposal/id :proposal/title :proposal/body
                     :proposal/pro-votes :proposal/con-votes
                     :proposal/opinion
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
      (input/button {:size      :small
                     :color     (if (pos? opinion) "primary" "default")
                     :variant   :text
                     :onClick   #(comp/transact! this [(add-opinion {:proposal/id id
                                                                     :opinion     (if (pos? opinion) 0 +1)})])
                     :startIcon (React/createElement ThumbUpAltTwoTone #js {:color "default"})}
        "Zustimmen")
      (input/button {:size      :small
                     :color     (if (neg? opinion) "primary" "default")
                     :variant   :text
                     :onClick   #(comp/transact! this [(add-opinion {:proposal/id id
                                                                     :opinion     (if (neg? opinion) 0 -1)})])
                     :startIcon (React/createElement ThumbDownAltTwoTone)}
        "Ablehnen"))))

(def ui-proposal (comp/computed-factory Proposal {:keyfn :proposal/id}))

(defn href-to-proposal-list []
  (routing/path->url
    (dr/path-to
      (comp/registry-key->class 'decide.ui.main-app/MainApp)
      (comp/registry-key->class 'decide.ui.main-app/MainProposalList))))

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

(defn vote-scale [{:proposal/keys [pro-votes con-votes] :or {pro-votes 0 con-votes 0}}]
  (log/info pro-votes con-votes)
  (layout/grid {:container  true
                :alignItems :center
                :justify    :space-between}
    (layout/grid {:item true :xs 1 :align :center} (str pro-votes))
    (layout/grid {:item true :xs 9}
      (vote-linear-progress
        {:variant "determinate"
         :value   (percent-of-pro-votes pro-votes con-votes)}))
    (layout/grid {:item true :xs 1 :align :center} (str con-votes))))
;; endregion

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
   :will-enter    (fn will-enter-proposal-page
                    [app {:keys [proposal-id]}]
                    (dr/route-deferred [:proposal/id proposal-id]
                      #(df/load! app [:proposal/id proposal-id] ProposalPage
                         {:post-mutation        `dr/target-ready
                          :post-mutation-params {:target (comp/get-ident ProposalPage {:proposal/id proposal-id})}})))}
  (layout/container {:maxWidth :lg}
    (breadcrumbs/breadcrumb-nav
      [["Vorschläge" (href-to-proposal-list)]
       [(str "#" id) ""]])
    (surfaces/card {}
      (surfaces/card-header {:title                    title
                             :subheader                (str "#" id)
                             :subheaderTypographyProps {:style {:display    "inline"
                                                                :marginLeft ".3rem"}}})
      ;; :action                   (input/icon-button {} (React/createElement MoreVertIcon))})

      (surfaces/card-content {:style {:paddingTop 0}}
        (dd/typography {:variant   "body2"
                        :color     "textSecondary"
                        :paragraph true}
          body)

        (when-not (empty? parents)
          (proposal-section
            (str "Dieser Vorschlag basiert auf " (count parents) " weiteren Vorschlägen")
            (dd/list {:dense false}
              (map ui-parent parents))))

        (proposal-section "Meinungen"
          (vote-scale props))

        (proposal-section "Argumente")))))
