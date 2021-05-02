(ns decide.models.argumentation
  (:require
    [clojure.string :as str]
    #?@(:clj  [[clojure.spec.alpha :as s]
               [clojure.spec.gen.alpha :as gen]
               [datahike.core :as d.core]]
        :cljs [[cljs.spec.alpha :as s]
               [cljs.spec.gen.alpha :as gen]])
    [com.fulcrologic.guardrails.core :refer [>defn => | <- ?]]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]))

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
    :db/cardinality :db.cardinality/one}])

;;; TODO Move the whole id stuff to a util ns
(s/def ::tempid/tempid
  (s/spec tempid/tempid?
    :gen #(gen/return (tempid/tempid))))

(s/def :argument/id (s/or :uuid uuid? :tempid ::tempid/tempid))
(s/def :argument/type #{:pro :contra})
(s/def :argument/premise (s/keys :req [:statement/id]))

(s/def :statement/id (s/or :uuid uuid? :tempid ::tempid/tempid))
(s/def :statement/content (s/and string? (complement str/blank?)))

(defn validate [spec x msg] ; move this to a util ns
  (when-not (s/valid? spec x)
    (throw (ex-info msg (s/explain-data spec x)))))

(>defn make-statement [{:statement/keys [id content] :as statement}]
  [(s/keys :req [:statement/content] :opt [:statement/id]) => (s/keys :req [:statement/id :statement/content])]
  (validate (s/keys :req [:statement/content]) statement "Statement invalid")
  {:statement/id (or id #?(:clj (d.core/squuid) :cljs (tempid/tempid)))
   :statement/content content})

(>defn make-argument
  ([] [=> (s/keys :req [:argument/id])] (make-argument {}))
  ([{:argument/keys [id type]}]
   [(s/keys :opt [:argument/id :argument/type]) => (s/keys :req [:argument/id])]
   (merge
     {:argument/id (or id #?(:clj (d.core/squuid) :cljs (tempid/tempid)))}
     (when type {:argument/type type}))))

(>defn make-argument-with-premise [statement]
  [(s/keys :req [:statement/content]) => (s/keys :req [:argument/id :argument/premise])]
  (-> (make-argument)
    (assoc :argument/premise (make-statement statement))))





