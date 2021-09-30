(ns decide.models.proposal.api
  (:require
    [clojure.set :as set]
    [com.fulcrologic.guardrails.core :refer [>defn => | <-]]
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [datahike.api :as d]
    [decide.models.proposal :as proposal]
    [decide.models.proposal.database :as proposal.db]
    [decide.models.process :as process]
    [decide.models.user :as user]))

(defresolver resolve-proposal [{:keys [db]} input]
  {::pc/input #{::proposal/id}
   ::pc/output [::proposal/id
                ::proposal/nice-id
                ::proposal/title ::proposal/body ::proposal/created
                {::proposal/original-author [::user/id ::user/display-name]}]
   ::pc/batch? true}
  (let [batch? (sequential? input)]
    (cond->> input
      (not batch?) vector
      :always (map #(find % ::proposal/id))
      :always (d/pull-many db [::proposal/id
                               ::proposal/nice-id
                               ::proposal/title ::proposal/body ::proposal/created
                               {::proposal/original-author [::user/id ::user/display-name]}])
      (not batch?) first)))

(defresolver resolve-parents [{:keys [db]} {::proposal/keys [id]}]
  {::pc/input #{::proposal/id}
   ::pc/output [{::proposal/parents [::proposal/id]}
                ::proposal/no-of-parents]}
  (let [proposal (or (d/pull db [{::proposal/parents [::proposal/id]}] [::proposal/id id]) {::proposal/parents []})]
    (assoc proposal ::proposal/no-of-parents (count (::proposal/parents proposal)))))

(defresolver resolve-no-of-parents [{:keys [db]} {::proposal/keys [id]}]
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
  (let [proposal (proposal.db/get-entity db id)
        process (get proposal ::process/_proposals)
        own-approvers (proposal.db/get-pro-voting-users db [::proposal/id id])]
    {:similar
     (if (process/single-approve? process)
       []
       (for [other-proposal-id (proposal.db/get-proposals-with-shared-opinion db [::proposal/id id])
             :let [other-approvers (proposal.db/get-pro-voting-users db [::proposal/id other-proposal-id])
                   own-uniques (count (set/difference own-approvers other-approvers))
                   common-uniques (count (set/intersection own-approvers other-approvers))
                   other-uniques (count (set/difference other-approvers own-approvers))]]
         {:own-proposal {::proposal/id id}
          :own-uniques own-uniques
          :common-uniques common-uniques
          :other-uniques (count (set/difference other-approvers own-approvers))
          :other-proposal {::proposal/id other-proposal-id}
          :sum-uniques (+ own-uniques common-uniques other-uniques)}))}))

(def full-api [resolve-proposal
               resolve-generation
               resolve-no-of-parents
               resolve-parents
               resolve-children

               resolve-child-relations
               resolve-parent-relations

               resolve-similar])