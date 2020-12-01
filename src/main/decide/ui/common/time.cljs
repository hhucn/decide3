(ns decide.ui.common.time)

(defn today? [^js/Date date]
  (let [now (js/Date.)]
    (and
      (= (.getFullYear date) (.getFullYear now))
      (= (.getMonth date) (.getMonth now))
      (= (.getDate date) (.getDate now)))))

(defn yesterday? [^js/Date date]
  (let [now (js/Date.)]
    (and
      (= (.getFullYear date) (.getFullYear now))
      (= (.getMonth date) (.getMonth now))
      (= (.getDate date) (dec (.getDate now))))))

(defn nice-string [^js/Date datetime]
  (let [hours (.getHours datetime)
        minutes (.getMinutes datetime)
        date-string (.toLocaleDateString datetime)]
    (cond
      (today? datetime) (str "heute um " hours ":" minutes " Uhr")
      (yesterday? datetime) (str "gestern um " hours ":" minutes " Uhr")
      :else (str " am " date-string))))
