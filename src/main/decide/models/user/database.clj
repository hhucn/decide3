(ns decide.models.user.database
  (:require
    [clojure.string :as str]
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


(defn find-by-nickname
  ([db nickname] (find-by-nickname db nickname ['*]))
  ([db nickname pattern] (d/pull db pattern [::user/email nickname])))


(defn find-by-id
  ([db user-id] (find-by-nickname db user-id ['*]))
  ([db user-id pattern] (d/pull db pattern [::user/id user-id])))


(defn search [db search-term pattern]
  (d/q '[:find (pull ?e pattern)
         :in $ ?search-term pattern
         :where
         [?e ::user/display-name ?dn]
         [(clojure.string/lower-case ?dn) ?lc-dn]
         [(clojure.string/includes? ?lc-dn ?search-term)]]
    db (str/lower-case search-term) pattern))