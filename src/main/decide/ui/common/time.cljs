(ns decide.ui.common.time
  (:require
    [cljs.spec.alpha :as s]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.guardrails.core :refer [>defn =>]]
    ["@date-io/date-fns" :as date-fns]
    ["@material-ui/pickers" :refer [DateTimePicker MuiPickersUtilsProvider]]))

(s/def :js/Date #(instance? js/Date %))

(>defn in-past? [^js/Date date]
  [:js/Date => boolean?]
  (let [now (js/Date.)]
    (< date now)))

(>defn today? [^js/Date date]
  [:js/Date => boolean?]
  (let [now (js/Date.)]
    (and
      (= (.getFullYear date) (.getFullYear now))
      (= (.getMonth date) (.getMonth now))
      (= (.getDate date) (.getDate now)))))

(>defn yesterday? [^js/Date date]
  [:js/Date => boolean?]
  (let [now (js/Date.)]
    (and
      (= (.getFullYear date) (.getFullYear now))
      (= (.getMonth date) (.getMonth now))
      (= (.getDate date) (dec (.getDate now))))))

(def short-time-format {:hour "numeric"})                   ; e.g. "13 Uhr"
(def long-time-format {:hour "numeric" :minute "2-digit"})  ; e.g. "13:37"
(def short-date-format {:day "numeric" :month "long"})      ; e.g. "14. Januar"
(def long-date-format {:day "numeric" :month "short" :year "numeric"}) ; e.g. "14. Jan. 2020"

(>defn format [^js/Date date format]
  [:js/Date map? => string?]
  (.toLocaleString date js/undefined (clj->js format)))

(>defn short-time? [^js/Date time] [:js/Date => boolean?] (zero? (.getMinutes time)))
(>defn short-year? [^js/Date date] [:js/Date => boolean?] (= (.getYear date) (.getYear (js/Date.))))

(>defn format-time [^js/Date time]
  [:js/Date => string?]
  (format time
    (if (short-time? time)
      short-time-format
      long-time-format)))

(>defn format-date [^js/Date date]
  [:js/Date => string?]
  (format date
    (if (short-year? date)
      short-date-format
      long-date-format)))

(>defn format-datetime [^js/Date datetime]
  [:js/Date => string?]
  (format datetime
    (cond-> (merge short-time-format short-date-format)
      (not (short-year? datetime)) (merge long-date-format)
      (not (short-time? datetime)) (merge long-time-format))))

(>defn nice-string
  ([^js/Date datetime] [:js/Date => string?] (nice-string datetime {}))
  ([^js/Date datetime {:keys [dateprefix]}]
   [:js/Date map? => string?]
   (cond
     (today? datetime) (str "heute um " (format-time datetime))
     (yesterday? datetime) (str "gestern um " (format-time datetime))
     :else (str dateprefix (format-datetime datetime)))))

(>defn time-element [^js/Date datetime & children]
  [:js/Date (s/* any?) => dom/element?]
  (dom/time {:dateTime (.toISOString datetime)
             :title datetime}
    (or
      children
      (format-datetime datetime))))

(defn datetime-picker [props]
  (dom/create-element MuiPickersUtilsProvider #js {:utils date-fns}
    (dom/create-element DateTimePicker
      (clj->js
        (merge
          {:ampm false
           :labelFunc (fn [date _invalidLabel] (nice-string date))}
          props)))))