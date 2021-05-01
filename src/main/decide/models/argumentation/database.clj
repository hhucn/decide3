(ns decide.models.argumentation.database
  (:require
    [clojure.spec.alpha :as s]
    [com.fulcrologic.guardrails.core :refer [>defn => | <- ?]]
    [datahike.core :as d.core]))

(defn validate [spec x msg] ; move this to a util ns
  (when-not (s/valid? spec x)
    (throw (ex-info msg (dissoc (s/explain-data spec x)
                          ::s/spec)))))

(>defn make-statement [{:statement/keys [id content] :as statement}]
  [(s/keys) => (s/keys :req [:statement/id])]
  (validate (s/keys :req [:statement/content]) statement "Statement invalid")
  {:statement/id (or id (d.core/squuid))
   :statement/content content})

(>defn make-argument [{:argument/keys [id type]}]
  [(s/keys) => (s/keys :req [:argument/id])]
  (merge
    {:argument/id (or id (d.core/squuid))}
    (when type {:argument/type type})))

