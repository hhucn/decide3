(ns decide.server-main
  (:require
    decide.server-components.http-server
    decide.server-components.nrepl
    decide.features.notifications.notifier
    [mount.core :as mount])
  (:gen-class))

;; This is a separate file for the uberjar only. We control the server in dev mode from src/dev/user.clj
(defn -main [& args]
  (mount/start-with-args {:config "config/prod.edn"}))
