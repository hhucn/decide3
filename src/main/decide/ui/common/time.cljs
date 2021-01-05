(ns decide.ui.common.time
  (:require
    [cljs.spec.alpha :as s]
    [com.fulcrologic.guardrails.core :refer [>defn =>]]))

(s/def :js/Date #(instance? js/Date %))

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

(>defn nice-string [^js/Date datetime]
  [:js/Date => string?]
  (let [hours (.getHours datetime)
        minutes (.getMinutes datetime)
        date-string (.toLocaleDateString datetime)]
    (cond
      (today? datetime) (str "heute um " hours ":" minutes " Uhr")
      (yesterday? datetime) (str "gestern um " hours ":" minutes " Uhr")
      :else (str " vom " date-string))))
