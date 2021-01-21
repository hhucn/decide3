(ns decide.models.opinion
  (:require
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [decide.models.proposal :as proposal]))

(defmutation add [{::proposal/keys [id]
                   :keys   [opinion]}]
  (action [{:keys [state]}]
    (swap! state update-in [::proposal/id id] assoc ::proposal/my-opinion opinion))
  (remote [_] true))