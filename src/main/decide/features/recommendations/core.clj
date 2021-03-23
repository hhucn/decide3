(ns decide.features.recommendations.core
  (:require
    [clojure.spec.alpha :as s]
    [com.fulcrologic.guardrails.core :refer [>defn => | ? <-]]
    [datahike.api :as d]
    [datahike.core :as d.core]
    [decide.models.opinion :as opinion]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]))

(>defn recommended-proposals [db {:keys [user process]}]
  [d.core/db? (s/keys :req-un [::process/process ::user/user])
   => (s/coll-of ::proposal/proposal)]
  (d/q
    '[:find [(pull ?proposal [::proposal/id]) ...]
      :in $ % ?process ?user
      :where
      [?process ::process/proposals ?proposal]
      (undecided? ?user ?proposal ?opinion)]
    db
    opinion/approves-rule
    (find process ::process/slug)
    (find user ::user/id)))