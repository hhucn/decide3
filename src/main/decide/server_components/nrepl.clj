(ns decide.server-components.nrepl
  (:require
    [mount.core :refer [defstate args]]
    [nrepl.server :refer [start-server stop-server]]
    [nrepl.transport :as transport]
    [decide.server-components.config :refer [config]]
    [taoensso.timbre :as log]))

(defstate socket-repl
  :start
  (let []
    (log/info "Start nREPL on port:" 5555)
    (start-server
      :port 5555
      :transport-fn transport/tty
      :greeting-fn transport/tty-greeting))
  :stop (stop-server socket-repl))