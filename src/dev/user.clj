(ns user
  (:require
    [clojure.tools.namespace.repl :as tools-ns :refer [set-refresh-dirs]]
    [expound.alpha :as expound]
    [clojure.spec.alpha :as s]
    [mount.core :as mount]
    [datahike.api :as d]
    ;; this is the top-level dependent component...mount will find the rest via ns requires
    [decide.server-components.http-server :refer [http-server]]
    [decide.server-components.database :refer [conn]]
    [decide.server-components.email :as email]
    decide.features.notifications.notifier
    [decide.server-components.nrepl :as nrepl]))

;; ==================== SERVER ====================
(set-refresh-dirs "src/main" "src/dev" "src/test")
;; Change the default output of spec to be more readable
(alter-var-root #'s/*explain-out* (constantly expound/printer))

(defn query-all-entities []
  (let [db (d/db conn)]
    (->> db
      (d/q '[:find [?e ...] :where [?e]])
      (d/pull-many db '[*])
      (sort-by :db/id))))

(defn query-all-processes []
  (let [db (d/db conn)]
    (->> db
      (d/q '[:find [(pull ?e [*]) ...]
             :where [?e :decide.models.process/slug]]))))

(defn start
  "Start the web server"
  []
  (->
    (mount/except [#'nrepl/socket-repl])
    (mount/swap-states
      {#'email/mailer-chan {:start email/dev-mailer
                            :stop #(email/close! email/mailer-chan)}})
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

