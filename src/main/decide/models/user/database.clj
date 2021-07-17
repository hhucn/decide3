(ns decide.models.user.database
  (:require
    [datahike.api :as d]
    [decide.models.user :as user]))

(defn- user-id-exists? [db user-id]
  (some? (d/q '[:find ?user .
                :in $ ?user-id
                :where
                [?user ::user/id ?user-id]]
           db user-id)))

(defn get-entity [db user-id]
  (when (user-id-exists? db user-id)
    (d/entity db [::user/id user-id])))