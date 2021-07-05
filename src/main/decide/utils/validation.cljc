(ns decide.utils.validation
  (:require
    [#?(:clj clojure.spec.alpha
        :cljs cljs.spec.alpha) :as s]))

(defn validate [spec x msg]
  (when-not (s/valid? spec x)
    (throw (ex-info msg (s/explain-data spec x)))))