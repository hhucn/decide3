(ns decide.server-components.access-checker
  (:require
    [decide.models.process.database :as process.db]
    [decide.models.proposal.core :as proposal.core]
    [decide.models.proposal.database :as proposal.db]
    [decide.models.user.database :as user.db]
    [decide.server-components.access-plugin :as access]
    [taoensso.timbre :as log]))

(defmulti check-access! (fn [_env [k v]] k))
(defmethod check-access! :decide.models.proposal/id
  [{:keys [db AUTH/user-id] :as env} input]
  (log/debug "Check access to: " input)
  (when
    (proposal.core/has-access?
      (proposal.db/get-entity db input)
      (user.db/get-entity db user-id))
    (access/allow! env input)))


(defmethod check-access! :decide.models.process/slug
  [{:keys [db AUTH/user-id] :as env} input]
  (log/debug "Check access to: " input)
  (when
    (process.db/has-access?
      (process.db/get-entity db input)
      (user.db/get-entity db user-id))
    (access/allow! env input)))

(defmethod check-access! :default [_ _] true)