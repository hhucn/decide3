(ns decide.models.opinion
  (:require
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [decide.models.proposal :as proposal]))

(defn set* [{::proposal/keys [my-opinion pro-votes] :as proposal} new-opinion]
  (assoc proposal
    ::proposal/my-opinion new-opinion
    ::proposal/pro-votes (+ pro-votes (- new-opinion my-opinion))))

(defmutation add [{::proposal/keys [id]
                   :keys [opinion]}]
  (action [{:keys [state]}]
    (swap! state update-in [::proposal/id id] set* opinion))
  (remote [_] true))