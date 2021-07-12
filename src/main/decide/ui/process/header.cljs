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
    [material-ui.feedback :as feedback]
    [material-ui.inputs :as inputs]
    [material-ui.lab.alert :as alert]
    [material-ui.layout :as layout]
    [material-ui.layout.grid :as grid]
    [material-ui.transitions :as transitions]
    ["@material-ui/icons/ExpandLess" :default ExpandLess]
    ["@material-ui/icons/ExpandMore" :default ExpandMore]
    [taoensso.timbre :as log]))


(defn process-ended-alert [{:keys [component]}]
  (alert/alert {:severity :success}
    (i18n/trf "Ended on the {endDatetime}!" {:endDatetime component})))

(defn process-ends-alert [{:keys [component]}]
  (alert/alert {:severity :info}
    (i18n/trf "Ends at {endDatetime}" {:endDatetime component})))

(defsc Moderator [_ _]
  {:query [::user/id]
   :ident ::user/id})

(defn ends-in-label [end-time]
  (let [ms-to-end (- end-time (js/Date.now))
        days-to-end (max 0 (Math/floor (/ ms-to-end (* 1000 60 60 24))))
        hours-to-end (max 0 (Math/floor (/ ms-to-end (* 1000 60 60))))
        minutes-to-end (max 0 (Math/floor (/ ms-to-end (* 1000 60))))]
    (cond
      (pos? days-to-end) (i18n/trf "{days, plural, =1 {# day} other {# days}} left" {:days days-to-end})
      (pos? hours-to-end) (i18n/trf "{hours, plural, =1 {# hour} other {# hours}} left" {:hours hours-to-end})
      :else (i18n/trf "{minutes, plural, =1 {# minute} other {# minutes}} left" {:minutes minutes-to-end}))))

(defn progress [start-time end-time]
  (let [process-duration (- end-time start-time)]
    (if (>= 0 process-duration)                      ; negative duration! don't divide by zero!
      100
      (let [elapsed (max 0 (- (js/Date.now) start-time))]
        (* 100 (/ elapsed process-duration))))))

(defsc Process [_ {::process/keys [title start-time end-time description] :as process}]
  {:query [::process/slug
           ::process/title
           ::process/description
           ::process/start-time
           ::process/end-time
           {::process/moderators (comp/get-query Moderator)}]
   :ident ::process/slug
   :initial-state
   (fn [{:keys [slug]}]
     (when slug
       {::process/slug slug}))
   :use-hooks? true}
  (let [[description-open? set-description-open] (hooks/use-state false)
        has-end-time? (some? end-time)
        has-start-time? (some? start-time)]
    (layout/box {:mx 2 :my 1}
      (dd/typography {:component "h1" :variant "h2"} title)


      (transitions/collapse {:in description-open?}
        (layout/box {:py 2 :clone true}
          (dd/typography {:variant :body1
                          :style {:whiteSpace :pre-line}}
            description)))
      (grid/container {:spacing 1 :alignItems :center}
        (grid/item {:xs 12 :sm :auto}
          (inputs/button {:variant (if has-end-time? :text :outlined)
                          :size (if has-end-time? :large :small)
                          :onClick #(set-description-open (not description-open?))
                          :endIcon (if description-open?
                                     (dom/create-element ExpandLess)
                                     (dom/create-element ExpandMore))}
            "Details"))
        (when has-end-time?
          (grid/item {:xs true}
            (if (process/over? process)
              (process-ended-alert {:component (time/nice-time-element end-time)})
              (alert/alert {:severity :info
                            :aria-hidden true}
                (dd/tooltip
                  {:title (str end-time)}
                  (alert/title {} (ends-in-label end-time)))
                (when (and has-start-time? has-end-time?)
                  (layout/box {:sx {:width "200px"}
                               :clone true}
                    (feedback/linear-progress
                      {:variant :determinate
                       :value (progress start-time end-time)})))))))))))


(def ui-process (comp/factory Process))

(defsc ProcessHeader [_ {:keys [ui/current-process]}]
  {:query
   [{[:ui/current-process '_] (comp/get-query Process)}]
   :initial-state {}}
  (ui-process current-process))

(def ui-process-header (comp/factory ProcessHeader))