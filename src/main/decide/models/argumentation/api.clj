(ns decide.models.argumentation.api
  (:require
   [com.wsscode.pathom.connect :as pc :refer [defmutation defresolver]]
   [com.wsscode.pathom.core :as p]
   [datahike.api :as d]
   [decide.models.argumentation :as argumentation]
   [decide.models.argumentation.database :as argumentation.db]
   [decide.models.proposal :as-alias proposal]
   [decide.models.proposal.database :as proposal.db]
   [decide.models.user :as user]
   [decide.server-components.eql-api.transformer :as transformer]))

(defresolver resolve-argument [{:keys [db]} {:argument/keys [id]}]
  {::pc/output [:argument/id
                :argument/type
                {:argument/premise [:statement/id
                                    :statement/content]}
                {:argument/conclusion [:statement/id
                                       :statement/content]}
                {:author [::user/id]}
                {:argument/author [::user/id]}]}
  (let [argument (d/pull db [:argument/id
                             :argument/type
                             {:argument/premise [:statement/id :statement/content]}
                             {:argument/conclusion [:statement/id :statement/content]}
                             {:author [::user/id]}]
                   [:argument/id id])
        user (:author argument)]
    (assoc argument :argument/author user)))

(defresolver resolve-skip-statement [{:keys [db]} {:argument/keys [id]}]
  {::pc/output [{:argument/premise->arguments [:argument/id]}]}
  (let [{:argument/keys [premise]}
        (d/pull db [{:argument/premise [{:argument/_conclusion [:argument/id]}]}]
          [:argument/id id])]
    {:argument/premise->arguments (get premise :argument/_conclusion [])}))


(defresolver resolve-statement [{:keys [db]} {:statement/keys [id]}]
  {::pc/output [:statement/id
                :statement/content
                {:statement/author [::user/id]}
                {:argument/_conclusion [:argument/id]}]}
  (let [{:keys [author] :as statement}
        (d/pull db [:statement/id :statement/content
                    {:author [::user/id]}
                    {:argument/_conclusion [:argument/id]}] [:statement/id id])]
    (cond-> statement
      :always (dissoc :author)
      author (assoc :statement/author author))))

(defresolver resolve-proposal-arguments [{:keys [db]} {::proposal/keys [id]}]
  {::pc/output [{::proposal/positions [:argument/id]}]}
  (let [{:keys [::proposal/arguments]} (d/pull db [{::proposal/arguments [:argument/id]}] [::proposal/id id])]
    {::proposal/positions (or arguments [])}))

(defresolver resolve-no-of-arguments [{:keys [db]} {::proposal/keys [id]}]
  {::proposal/no-of-arguments
   (proposal.db/get-no-of-arguments db {::proposal/id id})})

(defresolver resolve-no-of-sub-arguments [{:keys [db]} {:argument/keys [id]}]
  {:argument/no-of-arguments
   (proposal.db/get-no-of-sub-arguments db {:argument/id id})})


;;; region Mutations

(defmutation add-argument-to-statement
  [{:keys [AUTH/user db] :as env} {:keys [argument conclusion]}]
  {::pc/params #{:argument :conclusion}
   ::pc/output [:argument/id]
   ::pc/transform transformer/needs-login}
  (let [argument-tempid (:argument/id argument)


        premise         (:argument/premise argument)
        premise-tempid  (:statement/id premise)

        new-premise     (-> premise
                          (select-keys [:statement/content])
                          argumentation/make-statement
                          (assoc :author (:db/id user)))


        new-argument    (-> argument
                          (select-keys [:argument/type])
                          argumentation/make-argument
                          (assoc
                            :author (:db/id user)
                            :argument/premise new-premise))
        argument-id     (:argument/id new-argument)]
    (if-let [conclusion (argumentation.db/get-statement-by-id db (:statement/id conclusion))]
      (let [tx-report (argumentation.db/add-argument-to-statement! env conclusion new-argument)]
        {:tempids {argument-tempid (:argument/id new-argument)
                   premise-tempid (:statement/id new-premise)}
         ::p/env (assoc env :db (:db-after tx-report))
         :argument/id argument-id})
      (throw (ex-info "Conclusion not found" {:conclusion conclusion})))))

(defmutation add-argument-to-proposal
  [{:keys [AUTH/user db] :as env} {:keys [proposal argument]}]
  {::pc/params #{:argument :proposal}
   ::pc/output [:argument/id]
   ::pc/transform transformer/needs-login}
  (let [argument-tempid (:argument/id argument)

        premise         (:argument/premise argument)
        premise-tempid  (:statement/id premise)

        {real-premise-id :statement/id :as new-premise}
        (-> premise
          (select-keys [:statement/content])
          argumentation/make-statement
          (assoc :author (:db/id user)))

        ; Make sole argument
        {real-argument-id :argument/id :as new-argument}
        (-> argument
          (select-keys [:argument/type])
          argumentation/make-argument
          (assoc
            :author (:db/id user)
            :argument/premise new-premise))]

    (if-let [proposal-entity (proposal.db/get-by-id db (::proposal/id proposal))]
      (let [tx-report (argumentation.db/add-argument-to-proposal! env proposal-entity new-argument)]
        {:tempids {argument-tempid real-argument-id
                   premise-tempid real-premise-id}
         ::p/env (assoc env :db (:db-after tx-report))
         :argument/id real-argument-id})
      (throw (ex-info "Proposal not found" {:proposal proposal})))))
; endregion

(def full-api
  [add-argument-to-statement
   add-argument-to-proposal

   resolve-statement
   resolve-argument
   resolve-proposal-arguments
   resolve-skip-statement
   resolve-no-of-arguments
   resolve-no-of-sub-arguments])
