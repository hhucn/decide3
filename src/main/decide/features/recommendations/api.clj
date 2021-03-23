(ns decide.features.recommendations.api
  (:require
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [decide.features.recommendations.core :as core]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]))

(defresolver resolve-personal-proposal-recommendations [{:keys [db AUTH/user-id]} process]
  {::pc/input #{::process/slug}
   ::pc/output [{:MY/proposal-recommendations [::proposal/id]}]}
  (when user-id
    {:MY/proposal-recommendations
     (core/recommended-proposals db
       {:user {::user/id user-id}
        :process process})}))

(def all-resolvers
  [resolve-personal-proposal-recommendations])