(ns decide.features.recommendations.core
  (:require
    [clojure.spec.alpha :as s]
    [com.fulcrologic.guardrails.core :refer [>defn => | ? <-]]
    [datahike.api :as d]
    [datahike.core :as d.core]
    [decide.models.process :as-alias process]
    [decide.models.proposal :as-alias proposal]
    [decide.models.user :as user]
    [decide.schema :as schema]))

(>defn recommended-proposals [db {:keys [user process]}]
  [d.core/db? (s/keys :req-un [::process/process ::user/user])
   => (s/coll-of ::proposal/proposal)]
  (d/q
    '[:find [(pull ?undecided-descendant [::proposal/id]) ...]
      :in $ % ?process ?user
      :where
      [?process ::process/proposals ?proposal]
      (approves? ?user ?proposal)
      (ancestor ?proposal ?undecided-descendant)
      (undecided? ?user ?undecided-descendant)]
    db
    schema/rules
    (find process ::process/slug)
    (find user ::user/id)))