(ns decide.models.argumentation.database
  (:require
   [com.fulcrologic.guardrails.core :refer [=>]]
   [datahike.api :as d]
   [datahike.core :as d.core]
   [decide.argument :as-alias argument]
   [decide.models.argumentation :as argumentation]
   [decide.models.process :as process]
   [decide.models.process.database :as process.db]
   [decide.models.proposal :as-alias proposal]
   [decide.server-components.database :as db]))

(def rules
  '[[(sub-argument ?argument ?sub-argument)
     [?argument :argument/premise ?premise]
     [?sub-argument :argument/conclusion ?premise]]

    [(argument-member-of-proposal ?proposal ?argument)
     [?proposal ::proposal/arguments ?first-level-argument]
     (or
       [(ground ?first-level-argument) ?argument]
       (argument-member ?first-level-argument ?argument))]

    [(argument-member ?argument ?flat-sub-arguments)
     [?flat-sub-arguments ::argument/ancestors ?argument]]


    [(no-of-arguments ?proposal ?no-of-arguments)
     (argument-member-of-proposal ?proposal ?argument)
     [(sum? ?argument) ?no-of-arguments]]

    [(no-of-arguments ?argument ?no-of-arguments)
     [?flat-sub-arguments ::argument/ancestors ?argument]
     [(sum? ?flat-sub-arguments) ?no-of-arguments]]


    ;; Non-recursive rule for top-level arguments directly beneath a `?proposal`.
    [(belongs-to-proposal-1 ?argument ?proposal)
     [?proposal ::proposal/arguments ?argument]]


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


(defn get-argument-by-id [db id]
  (d/entity db [:argument/id id]))


(defn exists? [db argument-id]
  [d.core/db? :argument/id => boolean?]
  (some? (get-argument-by-id db argument-id)))


(defn belongs-to-process? [db process argument]
  (some?
    (d/q '[:find ?proposal
           :in $ % ?process ?argument
           :where
           [?process ::process/proposals ?proposal]
           (belongs-to-proposal ?proposal ?argument)]
      db
      rules
      (:db/id process)
      (:db/id argument))))

(defn get-statement-by-id [db id]
  (d/entity db [:statement/id id]))


(defn is-premise-of
  "Returns the argument of which the statement is a premise of."
  [statement]
  (first (:argument/_premise statement)))


(defn ->add-argument-to-statement [argument statement]
  (let [id (:db/id argument)]
    (into [[:db/add id :argument/conclusion (:db/id statement)]]
      (for [ancestor (::argument/ancestors (is-premise-of statement) [])]
        [:db/add id ::argument/ancestors (:db/id ancestor)]))))


(defn ->add-argument-to-proposal [argument proposal]
  [[:db/add (:db/id proposal) :decide.models.proposal/arguments (:db/id argument)]])


(defn ->add-argument [argument]
  [(merge
     (select-keys argument [:db/id :argument/id :argument/type :author :argument/premise])
     {::argument/ancestors [(:db/id argument)]})])


(defn add-argument-to-statement! [{:keys [conn AUTH/user]} statement argument]
  (let [process (-> statement is-premise-of argumentation/proposal ::process/_proposals first)]
    (process/must-be-running! process)
    (process/must-have-access! process user)
    (db/transact-as conn user
      {:tx-data
       (let [argument (db/with-tempid :argument/id argument)]
         (concat
           (->add-argument argument)
           (->add-argument-to-statement argument statement)
           (process.db/->add-participant process user)))})))


(defn add-argument-to-proposal! [{:keys [conn AUTH/user]} proposal argument]
  (let [process (-> proposal ::process/_proposals first)]
    (process/must-be-running! process)
    (process/must-have-access! process user)
    (db/transact-as conn user
      {:tx-data
       (let [argument (db/with-tempid :argument/id argument)]
         (concat
           (->add-argument argument)
           (->add-argument-to-proposal argument proposal)
           (process.db/->add-participant process user)))})))
