(ns decide.models.user.database
  (:require
    [datahike.api :as d]
    [decide.models.user :as user]
    [decide.server-components.database :as db]))

(defn- user-id-exists? [db user-id]
  (some? (d/q '[:find ?user .
                :in $ ?user-id
                :where
                [?user ::user/id ?user-id]]
           db user-id)))

(defn get-entity [db user-id]
  (when (user-id-exists? db user-id)
    (d/entity db [::user/id user-id])))

(defn ->update [user]
  (let [user-id (::user/id user)]
    (cons
      (-> user
        (assoc :db/id [::user/id user-id])
        (dissoc ::user/id))
      (db/retract-empty?-tx [::user/id user-id] user))))