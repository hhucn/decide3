(ns decide.models.user.api
  (:require
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.algorithms.merge :as mrg]
    [com.fulcrologic.fulcro.algorithms.normalized-state :as norm-state]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.raw.components :as rc]
    [decide.models.user :as user]
    [taoensso.timbre :as log]))

(defmutation update-user [user]
  (action [{:keys [state]}]
    (if (contains? user ::user/id)
      (norm-state/swap!-> state
        (mrg/merge-component (rc/entity->component user) user)
        (fs/entity->pristine* (find user ::user/id)))
      (log/error update-user " needs parameter: " ::user/id)))
  (remote [_] (contains? user ::user/id)))

(defmutation update-current [user]
  (action [{:keys [app state]}]
    (if-let [id (norm-state/get-in-graph @state [:root/current-session :user ::user/id])]
      (rc/transact! app [(update-user (assoc user ::user/id id))])
      (log/error "There is no current-user"))))
