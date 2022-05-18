(ns decide.database.migrate
  (:require
   [clojure.data :refer [diff]]
   [clojure.tools.logging]
   [datahike.api :as d]
   [ragtime.core :as ragtime]
   [ragtime.protocols :as protocols]
   [taoensso.timbre :as log]))

;;; Schema migration
(defn- index-by-ident [schema]
  (into {} (map (juxt :db/ident identity)) schema))

(defn- schema-difference [old-schema new-schema]
  (let [schema-index (index-by-ident old-schema)]
    (keep
      (fn [{:keys [db/ident] :as new-attribute}]
        (if-let [old-attribute (get schema-index ident)]
          (let [[_ new _] (diff old-attribute new-attribute)]
            (when new
              (assoc new :db/id (:db/id old-attribute))))
          new-attribute))
      new-schema)))

(defn- current-schema [db]
  (d/q '[:find [(pull ?e [*]) ...]
         :where [?e :db/ident]] db))

(def data-migrations-schema
  [{:db/ident ::migrations
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/many}
   {:db/ident ::applied-migrations}])

(defn upsert!
  "Transact only data in `new-schema` that isn't already present in the db."
  [conn new-schema]
  (log/info "Upsert schema!")
  (when-some [schema-to-add (seq (schema-difference
                                   (current-schema @conn)
                                   (concat new-schema data-migrations-schema)))]
    (log/infof "Updated schema detected. Upserting %d attributes." (count schema-to-add))
    (log/debug (vec schema-to-add))
    (d/transact conn {:tx-data (vec schema-to-add)})))


;;; Data migration


(defn has-attribute? [db ident]
  (not (empty? (d/q '[:find ?e
                      :in % ?ident
                      :where [?e :db/ident ?ident]]
                 db ident))))

(defrecord DatahikeStore [conn]
  protocols/DataStore
  (add-migration-id [_ id]
    (d/transact conn {:tx-data [[:db/add ::applied-migrations ::migrations id]]}))
  (remove-migration-id [_ _id] (throw (UnsupportedOperationException. "You shall not make breaking changes!")))
  (applied-migration-ids [_]
    (-> conn d/db (d/pull [::migrations] ::applied-migrations) ::migrations)))

(defn make-migration [{:keys [id up]}]
  (reify protocols/Migration
    (id [_] id)
    (run-up! [_ store]
      (d/transact (:conn store)
        [[:db.fn/call up]]))
    (run-down! [_ _] (throw (UnsupportedOperationException. "Run-down! is not supported!")))))

(defn migrate-data! [conn migrations]
  (let [store      (DatahikeStore. conn)
        migrations (map make-migration migrations)
        index      (ragtime/into-index migrations)]
    (ragtime/migrate-all store index migrations
      {:reporter (fn [_ _ id] (log/infof "Apply migration: %s" (str id)))})))
