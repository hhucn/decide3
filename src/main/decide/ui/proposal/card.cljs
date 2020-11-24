(ns decide.ui.proposal.card
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [decide.routing :as routing]
    [material-ui.data-display :as dd]
    [material-ui.inputs :as inputs]
    [material-ui.layout :as layout]
    [material-ui.surfaces :as surfaces]
    ["@material-ui/icons/ThumbUpAltTwoTone" :default ThumbUpAltTwoTone]
    ["@material-ui/icons/ThumbDownAltTwoTone" :default ThumbDownAltTwoTone]
    ["React" :as react]
    [com.fulcrologic.fulcro.dom :as dom]
    [taoensso.timbre :as log]
    [decide.models.proposal :as model]))



(defn today? [date]
  (let [now (js/Date.)]
    (and
      (= (.getFullYear date) (.getFullYear now))
      (= (.getMonth date) (.getMonth now))
      (= (.getDate date) (.getDate now)))))

(defn yesterday? [date]
  (let [now (js/Date.)]
    (and
      (= (.getFullYear date) (.getFullYear now))
      (= (.getMonth date) (.getMonth now))
      (= (.getDate date) (dec (.getDate now))))))


(defn time-string [created]
  (when created
    (let [now (js/Date.now)]
      (cond
        (today? created) (str "heute um " (.getHours created) ":" (.getMinutes created) " Uhr")
        (yesterday? created) (str "gestern um " (.getHours created) ":" (.getMinutes created) " Uhr")
        :else (.toLocaleDateString created)))))

(defn subheader [{:proposal/keys [id created original-author]}]
  (let [author-name (:profile/name original-author)]
    (comp/fragment
      (dom/data {:className "proposal-id"
                 :value     id}
        (str "#" (if (tempid/tempid? id) "?" id)))
      " · "
      (when author-name
        (comp/fragment
          "von " (dom/address (str author-name))))
      (when created
        (comp/fragment
          " am " (dom/time {:dateTime (some-> created .toISOString)} (time-string created)))))))

(defsc Proposal [this {:proposal/keys [id title body opinion created original-author] :as props}]
  {:query         (fn []
                    [:proposal/id :proposal/title :proposal/body
                     :proposal/pro-votes :proposal/con-votes
                     :proposal/created
                     :proposal/opinion
                     {:proposal/parents '...}
                     {:proposal/original-author [:profile/name]}])
   :ident         :proposal/id
   :initial-state (fn [{:keys [id title body]}]
                    #:proposal{:id        id
                               :title     title
                               :body      body
                               :pro-votes 0
                               :con-votes 0})
   :use-hooks?    true}
  (let [proposal-href (hooks/use-memo #(routing/path->url
                                         (dr/path-to
                                           (comp/registry-key->class 'decide.ui.main-app/MainApp)
                                           (comp/registry-key->class `ProposalPage)
                                           id)))]
    (layout/box {:width "100%" :clone true}
      (surfaces/card
        {:variant :outlined}

        (layout/box {:pb 0 :clone true}
          (surfaces/card-header
            {:title     title
             :subheader (subheader props)}))
        (surfaces/card-content {}
          (dd/typography
            {:component "p"
             :variant   "body2"
             :color     "textSecondary"
             :style     {:whiteSpace "pre-line"}}
            body))
        (surfaces/card-actions {}

          (inputs/button {:size      :small
                          :color     (if (pos? opinion) "primary" "default")
                          :variant   :text
                          :onClick   #(comp/transact! this [(model/add-opinion {:proposal/id id
                                                                                :opinion     (if (pos? opinion) 0 +1)})])
                          :startIcon (react/createElement ThumbUpAltTwoTone)}
            "Zustimmen")
          (inputs/button {:size      :small
                          :color     (if (neg? opinion) "primary" "default")
                          :variant   :text
                          :onClick   #(comp/transact! this [(model/add-opinion {:proposal/id id
                                                                                :opinion     (if (neg? opinion) 0 -1)})])
                          :startIcon (react/createElement ThumbDownAltTwoTone)}
            "Ablehnen")
          (layout/box {:clone true
                       :style {:marginLeft "auto"}}
            (inputs/button {:component "a"
                            :href      proposal-href} "Mehr")))))))

(def ui-proposal (comp/computed-factory Proposal {:keyfn :proposal/id}))