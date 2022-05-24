(ns decide.server-components.access-checker
  (:require
   [decide.models.process.database :as process.db]
   [decide.models.proposal.core :as proposal.core]
   [decide.models.proposal.database :as proposal.db]
   [me.ebbinghaus.pathom2-access-plugin.core :as access]
   [taoensso.timbre :as log]))

(defmulti *check-access! (fn [_env [k v]] k))
(defmethod *check-access! :decide.models.proposal/id
  [{:keys [db AUTH/user] :as env} [_ id]]
  (when-let [proposal (proposal.db/get-by-id db id)]
    (let [allowed? (proposal.core/has-access? proposal user)]
      (when allowed?
        (access/allow! env [:decide.models.proposal/id id]))
      allowed?)))


(defmethod *check-access! :decide.models.process/slug
  [{:keys [db AUTH/user] :as env} input]
  (when-let [process (process.db/get-entity-by-slug db (second input))]
    (when-let [allowed? (process.db/has-access? process user)]
      (access/allow! env input)
      allowed?)))

(defmethod *check-access! :default [_ _] true)

(defn check-access! [{:keys [AUTH/user-id] :as env} input]
  (let [allowed? (*check-access! env input)]
    (log/tracef "Check access to: %s => %s" input (if allowed? "allowed" "denied"))
    (when-not allowed? (log/reportf "%s was denied to %s" (str user-id) input))
    allowed?))