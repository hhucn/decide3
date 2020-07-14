(ns decide.models.todo
  (:require
    [datahike.api :as d]
    [datahike.core :as d.core]
    [ghostwheel.core :refer [>defn >defn- => | ? <-]]
    [clojure.spec.alpha :as s]
    [decide.models.user :as user]))

(s/def ::id uuid?)
(s/def ::task string?)
(s/def ::done? boolean?)
(s/def ::todo (s/keys :req [::id ::task ::done?]))

(def schema [{:db/ident       ::id
              :db/cardinality :db.cardinality/one
              :db/unique      :db.unique/identity
              :db/valueType   :db.type/uuid}
             {:db/ident       ::done?
              :db/cardinality :db.cardinality/one
              :db/valueType   :db.type/boolean
              :db/doc         "True if the todo is done."}
             {:db/ident       ::task
              :db/cardinality :db.cardinality/one
              :db/valueType   :db.type/string
              :db/doc         "The actual content of a todo"}
             {:db/ident       ::owner
              :db/cardinality :db.cardinality/one
              :db/valueType   :db.type/ref
              :db/doc         "`user` of the todo."}])

(>defn real-id []
  [=> ::id]
  (d.core/squuid))

(>defn update-todo! [conn id new-todo]
  [d.core/conn? ::id any? => any?]
  (d/transact conn [(merge new-todo
                      {:db/id [::id id]})]))


(>defn delete-todo! [conn id]
  [d.core/conn? ::id => any?]
  (d/transact conn [[:db.fn/retractEntity [::id id]]]))

(>defn all-todo-ids-for-user [db user-id]
  [d.core/db? ::user/id => (s/coll-of ::id)]
  (d/q
    '[:find [?id ...]
      :in $ ?user-id
      :where
      [?e ::id ?id]
      [?e ::owner ?owner]
      [?owner ::user/id ?user-id]]
    db user-id))

(>defn get-todo [db id]
  [d.core/db? ::id => ::todo]
  (d/pull db [::id ::task ::done?] [::id id]))