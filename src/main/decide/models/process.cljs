(ns decide.models.process
  (:require
    [cljs.spec.alpha :as s]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro.algorithms.merge :as mrg]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.guardrails.core :refer [>defn => ? | <-]]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]
    [decide.utils.time :as time]))

(s/def ::end-time inst?)

(>defn over? [{::keys [end-time]}]
  [(s/keys :opt [::end-time]) => boolean?]
  (if end-time
    (time/past? end-time)
    false))
