(ns analysis
  (:require
    [datahike.api :as d]
    [decide.server-components.database :refer [conn]]))

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

(defn query-process [slug]
  (d/q '[:find [(pull ?e [*]) ...]
         :in $ ?slug
         :where [?e :decide.models.process/slug ?slug]]
    (d/db conn) slug))

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
         [?proposal :decide.models.proposal/opinions ?opinion]
         [?user :decide.models.user/opinions ?opinion]]
    @conn 27 [:decide.models.process/slug "wie-richten-wir-die-kueche-ein"])


  (get-transaction @conn 536871481)

  (def db @conn)

  (d/pull db [{:decide.models.process/proposals
               [:decide.models.proposal/title
                {:decide.models.proposal/original-author [:decide.models.user/display-name
                                                          :decide.models.user/email]}]}]
    [:decide.models.process/slug "decide-experiment2"])

  (group-by first
    (d/q '[:find (pull ?user [:decide.models.user/email :decide.models.user/display-name])
           (pull ?proposal [:decide.models.proposal/title])
           :in $ ?process
           :where
           [?proposal :decide.models.proposal/original-author ?user]]
      db [:decide.models.process/slug "decide-experiment2"]))

  (defn pprint-by [f coll]
    (doseq [[k vals] (group-by f coll)]
      (print k)
      (pprint/print-table
        vals)
      (println)))

  (pprint-by :who
    (sort-by :when
      (d/q '[:find ?nick ?when
             :keys who when
             :where
             [?e :db/txUser ?who]
             [?e :db/txInstant ?when]
             [?who :decide.models.user/email ?nick]]
        (d/history db))))

  (d/pull @conn [:decide.models.user/display-name] 26))