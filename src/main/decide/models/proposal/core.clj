(ns decide.models.proposal.core
  (:require
    [datahike.api :as d]
    [decide.models.argumentation.database :as argumentation.db]
    [decide.models.opinion.database :as opinion.db]
    [decide.models.process :as process]
    [decide.models.process.database :as process.db]
    [decide.models.proposal :as proposal]
    [decide.models.proposal.database :as proposal.db]))

(defn add! [conn user process new-proposal]
  (let [db (d/db conn)
        new-proposal-id (:db/id new-proposal)]
    (cond
      (process/over? process)
      (throw (ex-info "Process is already over." (select-keys process [::process/slug ::process/end-time])))

      (not-every? #(proposal.db/belongs-to-process? % process) (::proposal/parents new-proposal))
      (throw
        (ex-info "Some parents do not belong to the process."
          {:invalid-parents (remove  #(proposal.db/belongs-to-process? % process) (::proposal/parents new-proposal))}))

      (not-every? #(argumentation.db/belongs-to-process? db % process) (::proposal/arguments new-proposal))
      (throw
        (ex-info "Some arguments do not belong to the process."
          {:invalid-parents (remove  #(argumentation.db/belongs-to-process? db % process)
                              (::proposal/arguments new-proposal))})))

    (d/transact conn
      {:tx-data
       (concat
         (process.db/->enter process user)
         [(-> new-proposal
            (assoc ::proposal/nice-id (process.db/new-nice-id! conn (::process/slug process)))
            (update ::proposal/parents #(map :db/id %)) ; no entities as nested maps.. :-/
            (update ::proposal/arguments #(map :db/id %)))
          [:db/add (:db/id process) ::process/proposals new-proposal-id]
          [:db/add "datomic.tx" :db/txUser (:db/id user)]]
         (opinion.db/->set db user process new-proposal +1))})))

(defn has-access? [proposal user]
  (let [process (::process/_proposals proposal)]
    (process.db/has-access? process user)))