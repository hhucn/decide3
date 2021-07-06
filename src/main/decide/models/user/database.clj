(ns decide.models.user.database
  (:require
    [datahike.api :as d]
    [decide.models.user :as user]))

(defn get-entity [db user-id]
  (d/entity db [::user/id user-id]))