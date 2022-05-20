(ns decide.models.argumentation.database
  (:require
   [com.fulcrologic.guardrails.core :refer [=>]]
   [datahike.api :as d]
   [datahike.core :as d.core]
   [decide.models.argumentation :as argumentation]
   [decide.models.process :as process]))

(def argumentation-rules
  '[[(sub-argument ?argument ?sub-argument)
     [?argument :argument/premise ?premise]
     [?sub-argument :argument/conclusion ?premise]]


    [(no-of-arguments ?proposal ?no-of-arguments)
     [?proposal :decide.models.proposal/positions ?argument]
     (no-of-arguments ?argument ?no-of-sub-arguments)
     [(sum ?no-of-sub-arguments) ?no-of-arguments]]

    [(no-of-arguments ?argument ?no-of-arguments)
     [(ground 1) ?no-of-arguments]
     (not [?argument :argument/premise])]

    [(no-of-arguments ?argument ?no-of-this+subarguments)
     (sub-argument ?argument ?sub-arguments)
     (no-of-arguments ?sub-arguments ?no-of-arguments)
     [(sum ?no-of-arguments) ?sum-arguments]
     [(inc ?sum-arguments) ?no-of-this+subarguments]]


    ;; Non-recursive rule for top-level arguments directly beneath a `?proposal`.
    [(belongs-to-proposal-1 ?argument ?proposal)
     [?proposal :decide.models.proposal/arguments ?argument]]


    ;; Recursive rule for if an argument is connected to a proposal transitively.
    [(belongs-to-proposal ?argument ?proposal)
     (belongs-to-proposal-1 ?argument ?proposal)]

    [(belongs-to-proposal ?argument ?proposal)
     (sub-argument ?parent ?argument)
     (belongs-to-proposal ?parent ?proposal)]


    ;; Used to query for a argumentation path between a specific argument and an argument
    ;; Proposal X <- Argument A <- Argument B <- Argument C
    ;; (super-argument-root-path X C ?argument-path)
    ;; => ?argument-path = [A B C]
    ;; (?proposal is not really needed for now. But maybe in the future, when arguments may be reused.)
    ;; first-level argument
    [(super-argument-root-path ?proposal ?argument ?argument-path)
     (belongs-to-proposal-1 ?argument ?proposal)
     [(vector ?argument) ?argument-path]]

    [(super-argument-root-path ?proposal ?argument ?argument-path)
     (sub-argument ?super-argument ?argument)
     (super-argument-root-path ?proposal ?super-argument ?super-argument-path)
     [(clojure.core/conj ?super-argument-path ?argument) ?argument-path]]])

(defn exists? [db argument-id]
  [d.core/db? :argument/id => boolean?]
  (some? (d/q '[:find ?e . :in $ ?argument-id :where [?e :argument/id ?argument-id]] db argument-id)))

(defn belongs-to-process? [db process argument]
  (some?
    (d/q '[:find ?proposal
           :in $ % ?process ?argument
           :where
           [?process ::process/proposals ?proposal]
           (belongs-to-proposal ?proposal ?argument)]
      db
      argumentation-rules
      (:db/id process)
      (:db/id argument))))

(defn find-statement-by-id [db statement-id]
  (d/entity db [:statement/id statement-id]))

(defn add-argument-to-statement! [{:keys [conn AUTH/user]} statement argument]
  (throw (ex-info "bäm" (assoc argument
                          :argument/conclusion (:db/id statement))))
  (let [parent-argument (:argument/_premise statement)
        process         (::process/_proposals (argumentation/proposal parent-argument))]
    (cond
      (and (some? process) (process/running? process))
      (throw (ex-info "Process is not running" {:process (select-keys process [::process/slug ::process/start-time ::process/end-time])}))

      (and (process/private? process) (not (process/participant? process user)))
      (throw (ex-info "User has to be participant of process." {:process (select-keys process [::process/slug ::process/type])}))

      :else
      (d/transact conn
        {:tx-data
         [(assoc argument
            :db/id "new-argument"
            :decide.argument/ancestors (conj (map :db/id (:decide.argument/ancestors parent-argument)) "new-argument")
            :argument/conclusion (:db/id statement))
          [:db/add "datomic.tx" :tx/by (:db/id user)]]}))))
