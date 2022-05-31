(ns user
  (:require
   [clojure.spec.alpha :as s]
   [clojure.tools.namespace.repl :as tools-ns :refer [set-refresh-dirs]]
   [decide.features.notifications.notifier :as notifier]
   ;; this is the top-level dependent component...mount will find the rest via ns requires
   [decide.server-components.email :as email]
   [decide.server-components.http-server]
   [decide.server-components.nrepl :as nrepl]
   [expound.alpha :as expound]
   [mount.core :as mount]))

;; ==================== SERVER ====================
(set-refresh-dirs "src/main" "src/dev" "src/test" "components")
;; Change the default output of spec to be more readable
(alter-var-root #'s/*explain-out* (constantly expound/printer))

(defn start
  "Start the web server"
  []
  (->
    (mount/find-all-states)
    (mount/swap-states
      {#'email/mailer-chan {:start email/dev-mailer
                            :stop #(email/close! email/mailer-chan)}})
    (mount/except [#'nrepl/socket-repl #'notifier/notifier #'email/mailer-chan])
    mount/start))

(defn stop
  "Stop the web server"
  [] (mount/stop))

(defn restart
  "Stop, reload code, and restart the server. If there is a compile error, use:

  ```
  (tools-ns/refresh)
  ```

  to recompile, and then use `start` once things are good."
  []
  (stop)
  (tools-ns/refresh :after 'user/start))

