(ns decide.models.argumentation
  (:require
   #?@(:clj  [[clojure.spec.alpha :as s]
              [clojure.spec.gen.alpha :as gen]
              [datahike.core :as d.core]]
       :cljs [[cljs.spec.alpha :as s]
              [cljs.spec.gen.alpha :as gen]])
   [com.fulcrologic.guardrails.core :refer [>def >defn =>]]
   [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
   [decide.argument :as-alias argument]
   [decide.argumentation]
   [decide.models.proposal :as-alias proposal]
   [decide.statement :as-alias statement]))

(def schema
  [{:db/ident :author
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}

   {:db/ident :statement/id
    :db/unique :db.unique/identity
    :db/valueType :db.type/uuid
    :db/cardinality :db.cardinality/one}
   {:db/ident :statement/content
    :db/valueType :db.type/string
    ; :db/fulltext    true
    :db/cardinality :db.cardinality/one}

   {:db/ident :argument/id
    :db/unique :db.unique/identity
    :db/valueType :db.type/uuid
    :db/cardinality :db.cardinality/one}
   {:db/ident :argument/conclusion
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}
   {:db/ident :argument/premise
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}
   {:db/ident :argument/type
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one}

   {:db/ident ::argument/ancestors
    :db/doc "All ancestors of an argument up to the root argument(s). This enables improved query performance."
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many}])

;;; TODO Move the whole id stuff to a util ns
(>def ::tempid/tempid
  (s/spec tempid/tempid?
    :gen #(gen/return (tempid/tempid))))

(>def :argument/id (s/or :main ::argument/id :tempid ::tempid/tempid))
(>def :argument/type ::argument/type)
(>def :argument/premise
  (s/or
    :legacy (s/keys :req [:statement/id])
    :main ::argument/premise))
(>def :argument/entity (s/and associative? #(contains? % :db/id)))

(>def :statement/id (s/or :main ::statement/id :tempid ::tempid/tempid))
(>def :statement/content ::statement/content)
(>def :statement/entity (s/and associative? #(contains? % :db/id)))

(defn validate [spec x msg]                                 ; move this to a util ns
  (when-not (s/valid? spec x)
    (throw (ex-info msg (s/explain-data spec x)))))

(>defn make-statement [{:statement/keys [id content]
                        :or {id #?(:clj  (d.core/squuid)
                                   :cljs (tempid/tempid))}}]
  [(s/keys :req [:statement/content] :opt [:statement/id]) => (s/keys :req [:statement/id :statement/content])]
  #:statement{:id id
              :content content})

(>defn make-argument
  ([] [=> (s/keys :req [:argument/id])] (make-argument {}))
  ([{:argument/keys [id type]
     :or {id #?(:clj  (d.core/squuid)
                :cljs (tempid/tempid))}}]
   [(s/keys :opt [:argument/id :argument/type]) => (s/keys :req [:argument/id])]
   (merge
     {:argument/id id}
     (when type {:argument/type type}))))

(defn proposal [argument]
  (let [ancestors (::argument/ancestors argument)]
    (some ::proposal/_arguments ancestors)))





