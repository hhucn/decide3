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
    [decide.features.notifications.notifier :as notifier]
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

(defn get-all-transactions-by [db user-eid]
  (->>
    (d/q '[:find [(pull ?e [*]) ...]
           :in $ ?user
           :where
           [?e :db/txInstant]
           [?e :db/txUser ?user]]
      db user-eid)
    (sort-by :db/txInstant)))

(defn get-transaction [db tx]
  (d/q '[:find [?e ?a ?v ?tx ?op]
         :in $ ?tx
         :where
         [?e ?a ?v ?tx ?op]]
    db tx))

(comment
  (get-transaction (d/history (d/since @conn #inst"2021-07-13")) 536871519)

  (d/q '[:find ?e
         :where
         [?e :decide.models.user/display-name "Christian"]] @conn) ; => 26, 27, 28

  (let [db (d/since @conn #inst"2021-07-13")]
    (->>
      (get-all-transactions-by db 26)
      (map :db/id)
      (map #(get-transaction db %))))


  (map #(get-transaction @conn %)
    (d/q '[:find [?tx ...]
           :where
           [?process :decide.models.process/slug "wie-richten-wir-die-kueche-ein"]
           [?process :process/features _ ?tx]]
      @conn))

  (query-all-entities)

  (d/datoms @conn {:index :aevt :components [:decide.models.opinion/value]})
  (d/pull @conn [:decide.models.opinion/value {:decide.models.proposal/_opinions [:decide.models.proposal/title]}] 523)
  (d/pull @conn ['*] 536871546)

  (d/pull @conn [{:decide.models.user/opinions [:db/id :decide.models.opinion/value]}] 26)

  (d/q '[:find (pull ?user [:db/id :decide.models.user/display-name])
               (pull ?proposal [:db/id :decide.models.proposal/title])
               (pull ?opinion [:db/id :decide.models.opinion/value])
         :in $ ?user ?process
         :where
         [?process :decide.models.process/proposals ?proposal]
         [?user :decide.models.user/opinions ?opinion]
         [?proposal :decide.models.proposal/opinions ?opinion]]
    @conn 27 [:decide.models.process/slug "wie-richten-wir-die-kueche-ein"])


  (get-transaction @conn 536871481)





  (d/pull @conn [:decide.models.user/display-name] 26))

(defn query-process [slug]
  (d/q '[:find [(pull ?e [*]) ...]
         :in $ ?slug
         :where [?e :decide.models.process/slug ?slug]]
    (d/db conn) slug))

(defn start
  "Start the web server"
  []
  (-> (mount/find-all-states)
    (mount/except [#'nrepl/socket-repl #'notifier/notifier])
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

