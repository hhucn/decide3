(ns decide.models.proposal.api
  (:require
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [com.fulcrologic.guardrails.core :refer [>defn => | <-]]
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [datahike.api :as d]
    [datahike.core :as d.core]
    [decide.models.argument :as argument]
    [decide.models.authorization :as auth]
    [decide.models.proposal :as proposal]
    [decide.models.proposal.database :as proposal.db]
    [decide.models.user :as user]
    [decide.schema :as schema]))

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

(defresolver resolve-children [{:keys [db]} {::proposal/keys [id]}]
  {::pc/input #{::proposal/id}
   ::pc/output [{::proposal/children [::proposal/id]}]}
  {::proposal/children (proposal.db/get-children db [::proposal/id id])})

(defresolver resolve-generation [{:keys [db]} proposal]
  {::pc/input #{::proposal/id}
   ::pc/output [::proposal/generation]}
  {::proposal/generation (proposal.db/get-generation db proposal)})

(defresolver resolve-child-relations [{:keys [db]} {::keys [id]}]
  {::pc/output [{:child-relations [{:proposal [::id]}
                                   :migration-rate]}]}
  {:child-relations
   (for [{child-id ::id :as child} (proposal.db/get-children db [::id id])]
     {:proposal child
      :migration-rate (proposal.db/get-migration-rate db id child-id)})})

(defresolver resolve-parent-relations [{:keys [db]} {::keys [id]}]
  {::pc/output [{:parent-relations [{:proposal [::id]}
                                    :migration-rate]}]}
  {:parent-relations
   (for [{parent-id ::id :as parent} (proposal.db/get-parents db [::id id])]
     {:proposal parent
      :migration-rate (proposal.db/get-migration-rate db parent-id id)})})

(defresolver resolve-similar [{:keys [db]} {::keys [id]}]
  {::pc/input #{::id}
   ::pc/output [{:similar [{:own-proposal [::id]}
                           :own-uniques
                           :common-uniques
                           {:other-proposal [::id]}
                           :other-uniques
                           :sum-uniques]}]}
  (let [own-approvers (proposal.db/get-pro-voting-users db [::id id])]
    {:similar
     (for [other-proposal-id (proposal.db/get-proposals-with-shared-opinion db [::id id])
           :let [other-approvers (proposal.db/get-pro-voting-users db [::id other-proposal-id])
                 own-uniques (count (set/difference own-approvers other-approvers))
                 common-uniques (count (set/intersection own-approvers other-approvers))
                 other-uniques (count (set/difference other-approvers own-approvers))]]
       {:own-proposal {::id id}
        :own-uniques own-uniques
        :common-uniques common-uniques
        :other-uniques (count (set/difference other-approvers own-approvers))
        :other-proposal {::id other-proposal-id}
        :sum-uniques (+ own-uniques common-uniques other-uniques)})}))

(def full-api [resolve-proposal
               resolve-generation
               resolve-parents

               resolve-child-relations
               resolve-parent-relations])