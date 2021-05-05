(ns decide.ui.process.header
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.react.hooks :as hooks]
    [decide.models.process :as process]
    [decide.models.user :as user]
    [decide.utils.time :as time]
    [material-ui.data-display :as dd]
    [material-ui.inputs :as inputs]
    [material-ui.lab.alert :as alert]
    [material-ui.layout :as layout]
    [material-ui.layout.grid :as grid]
    [material-ui.transitions :as transitions]
    ["@material-ui/icons/ExpandLess" :default ExpandLess]
    ["@material-ui/icons/ExpandMore" :default ExpandMore]))


(defn process-ended-alert [{:keys [component]}]
  (alert/alert {:severity :success}
    (i18n/trf "Ended on the {endDatetime}!" {:endDatetime component})))

(defn process-ends-alert [{:keys [component]}]
  (alert/alert {:severity :info}
    (i18n/trf "Ends at {endDatetime}" {:endDatetime component})))

(defsc Moderator [_ _]
  {:query [::user/id]
   :ident ::user/id})

(defsc Process [_ {::process/keys [title end-time description] :as process}]
  {:query [::process/slug
           ::process/title
           ::process/description
           ::process/end-time
           {::process/moderators (comp/get-query Moderator)}]
   :ident ::process/slug
   :initial-state
   (fn [{:keys [slug]}]
     (when slug
       {::process/slug slug}))
   :use-hooks? true}
  (let [[description-open? set-description-open] (hooks/use-state false)
        has-end-time? (some? end-time)]
    (layout/box {:mx 2 :my 1}
      (dd/typography {:component "h1" :variant "h2"} title)


      (transitions/collapse {:in description-open?}
        (dd/typography {:variant :body1
                        :style {:whiteSpace :pre-line}}
          description))
      (grid/container {:spacing 1 :alignItems :center}
        (grid/item {}
          (inputs/button {:variant (if has-end-time? :text :outlined)
                          :size (if has-end-time? :large :small)
                          :onClick #(set-description-open (not description-open?))
                          :endIcon (if description-open?
                                     (dom/create-element ExpandLess)
                                     (dom/create-element ExpandMore))}
            "Details"))
        (when has-end-time?
          (grid/item {:xs true}
            (let [end-element (time/nice-time-element end-time)]
              (if (process/over? process)
                (process-ended-alert {:component end-element})
                (process-ends-alert {:component end-element})))))))))

(def ui-process (comp/factory Process))

(defsc ProcessHeader [_ {:keys [ui/current-process]}]
  {:query
   [{[:ui/current-process '_] (comp/get-query Process)}]
   :initial-state {}}
  (ui-process current-process))

(def ui-process-header (comp/factory ProcessHeader))