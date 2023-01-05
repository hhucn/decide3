(ns decide.models.proposal.list-api
  (:require
   [com.wsscode.pathom.connect :as pc :refer [defresolver]]
   [datahike.api :as d]
   [decide.models.process :as process]
   [decide.models.proposal :as proposal]
   [decide.proposal-list :as-alias proposal-list]
   [decide.proposal-list-entry :as-alias proposal-list-entry]
   [taoensso.timbre :as log]))


(defresolver resolve-proposal-list [{:keys [db]} process]
  {::pc/input #{::process/slug}
   ::pc/params [::proposal-list/order ::proposal-list/limit]
   ::pc/output [{::process/proposal-list
                 [{::proposal-list/entries
                   [::proposal-list-entry/position
                    {::proposal-list-entry/proposal [::proposal/id]}]}]}]}
  (let [proposals (::process/proposals
                   (d/pull db [{::process/proposals [::proposal/id]}]
                     (find process ::process/slug)))
        proposals->entries
                  (map-indexed
                    (fn [index proposal]
                      #::proposal-list-entry{:position index
                                             :proposal proposal})
                    proposals)]
    {::process/proposal-list
     {::proposal-list/entries (log/spy :debug (vec proposals->entries))}}))


(def all-resolvers [resolve-proposal-list])