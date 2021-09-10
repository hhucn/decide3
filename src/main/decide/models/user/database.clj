(ns decide.models.user.database
  (:require
    [datahike.api :as d]
    [decide.models.user :as user]
    [decide.server-components.database :as db]))

(defn entity [db user-id]
  (d/entity db [::user/id user-id]))

(defn ->update [user]
  (let [user-id (::user/id user)]
    (cons
      (-> user
        (assoc :db/id [::user/id user-id])
        (dissoc ::user/id))
      (db/retract-empty?-tx [::user/id user-id] user))))