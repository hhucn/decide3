(ns decide.server-components.access-checker
  (:require
    [decide.models.process.database :as process.db]
    [decide.models.proposal.core :as proposal.core]
    [decide.models.proposal.database :as proposal.db]
    [decide.models.user.database :as user.db]
    [decide.server-components.access-plugin :as access]
    [taoensso.timbre :as log]))

(defmulti *check-access! (fn [_env [k v]] k))
(defmethod *check-access! :decide.models.proposal/id
  [{:keys [db AUTH/user-id] :as env} input]
  (let [allowed? (proposal.core/has-access?
                   (proposal.db/get-entity db input)
                   (user.db/get-entity db user-id))]
    (when allowed?
      (access/allow! env input))
    allowed?))


(defmethod *check-access! :decide.models.process/slug
  [{:keys [db AUTH/user-id] :as env} input]
  (when-let [process (process.db/get-by-slug db (second input))]
    (let [allowed? (process.db/has-access?
                     process
                     (user.db/get-entity db user-id))]
      (when allowed?
        (access/allow! env input))
      allowed?)))

(defmethod *check-access! :default [_ _] true)

(defn check-access! [{:keys [AUTH/user-id] :as env} input]
  (let [allowed? (*check-access! env input)]
    (log/debugf "Check access to: %s => %s" input (if allowed? "allowed" "denied"))
    (when-not allowed? (log/reportf "%s was denied to %s" (str user-id) input))
    allowed?))