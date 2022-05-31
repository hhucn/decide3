(ns decide.models.proposal.api
  (:require
   [clojure.set :as set]
   [com.wsscode.pathom.connect :as pc :refer [defresolver]]
   [com.wsscode.pathom3.connect.operation :as pco]
   [com.wsscode.pathom3.connect.planner :as-alias pcp]
   [datahike.api :as d]
   [decide.models.process :as process]
   [decide.models.proposal :as proposal]
   [decide.models.proposal.database :as proposal.db]
   [decide.models.user :as user]))


(pco/defresolver resolve-proposal [{:keys [db]} {::proposal/keys [id]}]
  {::pco/input [::proposal/id]
   ::pco/output [::proposal/id
                 ::proposal/nice-id
                 ::proposal/title ::proposal/body ::proposal/created
                 {::proposal/original-author [::user/id ::user/display-name]}]}
  (d/pull db
    [::proposal/id
     ::proposal/nice-id
     ::proposal/title ::proposal/body ::proposal/created
     {::proposal/original-author [::user/id ::user/display-name]}]
    [::proposal/id id]))

(pco/defresolver resolve-parents [{:keys [db]} {::proposal/keys [id]}]
  {::pco/input [::proposal/id]
   ::pco/output [{::proposal/parents [::proposal/id]}
                 ::proposal/no-of-parents]}
  (let [proposal (or (d/pull db [{::proposal/parents [::proposal/id]}] [::proposal/id id]) {::proposal/parents []})]
    (assoc proposal ::proposal/no-of-parents (count (::proposal/parents proposal)))))

(pco/defresolver resolve-no-of-parents [{:keys [db]} {::proposal/keys [id]}]
  {::pco/priority 2}
  {::proposal/no-of-parents
   (d/q '[:find (count ?e) .
          :in $ ?proposal
          :where
          [?proposal ::proposal/parents ?e]]
     db [::proposal/id id])})

(defresolver resolve-children [{:keys [db]} {::proposal/keys [id]}]
  {::pc/input #{::proposal/id}
   ::pc/output [{::proposal/children [::proposal/id]}
                ::proposal/no-of-children]}
  (let [children (proposal.db/get-children db [::proposal/id id])]
    {::proposal/children children
     ::proposal/no-of-children (count children)}))

;; Todo get-generation is really slow.
(defresolver resolve-generation [{:keys [db]} proposal]
  {::pc/input #{::proposal/id}
   ::pc/output [::proposal/generation]}
  {::proposal/generation nil #_(proposal.db/get-generation db proposal)})

(defresolver resolve-child-relations [{:keys [db]} {::proposal/keys [id]}]
  {::pc/output [{:child-relations [{:proposal [::proposal/id]}
                                   :migration-rate]}]}
  {:child-relations
   (for [{child-id ::proposal/id :as child} (proposal.db/get-children db [::proposal/id id])]
     {:proposal child
      :migration-rate (proposal.db/get-migration-rate db id child-id)})})

(defresolver resolve-parent-relations [{:keys [db]} {::proposal/keys [id]}]
  {::pc/output [{:parent-relations [{:proposal [::proposal/id]}
                                    :migration-rate]}]}
  {:parent-relations
   (for [{parent-id ::proposal/id :as parent} (proposal.db/get-parents db [::proposal/id id])]
     {:proposal parent
      :migration-rate (proposal.db/get-migration-rate db parent-id id)})})

(defresolver resolve-similar [{:keys [db]} {::proposal/keys [id]}]
  {::pc/input #{::proposal/id}
   ::pc/output [{:similar [{:own-proposal [::proposal/id]}
                           :own-uniques
                           :common-uniques
                           {:other-proposal [::proposal/id]}
                           :other-uniques
                           :sum-uniques]}]}
  (let [proposal      (proposal.db/get-by-id db id)
        process       (get proposal ::process/_proposals)
        own-approvers (proposal.db/get-pro-voting-users db (:db/id proposal))]
    {:similar
     (if (process/single-approve? process)
       []
       (for [other-proposal-id (proposal.db/get-proposals-with-shared-opinion db (:db/id proposal))
             :let [other-approvers (proposal.db/get-pro-voting-users db [::proposal/id other-proposal-id])
                   own-uniques     (count (set/difference own-approvers other-approvers))
                   common-uniques  (count (set/intersection own-approvers other-approvers))
                   other-uniques   (count (set/difference other-approvers own-approvers))]]
         {:own-proposal {::proposal/id id}
          :own-uniques own-uniques
          :common-uniques common-uniques
          :other-uniques (count (set/difference other-approvers own-approvers))
          :other-proposal {::proposal/id other-proposal-id}
          :sum-uniques (+ own-uniques common-uniques other-uniques)}))}))

(def full-api [resolve-proposal
               resolve-generation
               resolve-parents
               resolve-no-of-parents
               resolve-children

               resolve-child-relations
               resolve-parent-relations

               resolve-similar])