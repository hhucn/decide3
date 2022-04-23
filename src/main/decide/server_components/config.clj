(ns decide.server-components.config
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [com.fulcrologic.fulcro.server.config :refer [load-config!]]
   [mount.core :refer [args defstate]]
   [taoensso.timbre :as log]))

(defn prettify-data [data]
  (if (string? data)
    data
    (with-out-str (pprint/pprint data))))

(defn pretty-print-middleware [{:keys [level] :as data}]
  (cond-> data
    (#{:debug} level) (update :vargs (partial mapv prettify-data))))

(defn configure-logging! [config]
  (let [{:keys [taoensso.timbre/logging-config]} config]
    (log/info "Configuring Timbre with " logging-config)
    (-> logging-config
      (assoc :middleware [pretty-print-middleware])
      log/merge-config!)))

(defn scripts-manifest [path]
  (some-> path io/resource slurp edn/read-string))

(defstate config
  :start
  (let [{:keys [config] :or {config "config/dev.edn"}} (args)
        configuration (load-config! {:config-path config})]
    (log/info "Loaded config" config)
    (configure-logging! configuration)
    (-> configuration
      (assoc :script-manifest (scripts-manifest "public/js/main/manifest.edn")))))

