(ns decide.models.argumentation.migrations
  (:require [datahike.api :as d]))

(def rules
  '[[(sub-argument ?argument ?sub-argument)
     [?argument :argument/premise ?premise]
     [?sub-argument :argument/conclusion ?premise]]

    ;; Used to query for a argumentation path between a specific argument and an argument
    ;; Proposal X <- Argument A <- Argument B <- Argument C
    ;; (ancestors X C ?argument-path)
    ;; => ?argument-path = [A B C]
    ;; (?proposal is not really needed for now. But maybe in the future, when arguments may be reused.)
    ;; first-level argument
    [(ancestors ?argument ?ancestors)
     [(vector ?argument) ?ancestors]]
    
    [(ancestors ?argument ?ancestors)
     (sub-argument ?super-argument ?argument)
     (ancestors ?super-argument ?parent-ancestors)
     [(clojure.core/conj ?parent-ancestors ?argument) ?ancestors]]])


(defn add-ancestors [db]
  (d/q
    '[:find ?argument ?ancestors
      :keys db/id decide.argument/ancestors
      :in $ %
      :where
      [?argument :argument/id]
      (not [?argument :decide.argument/ancestors])
      (ancestors ?argument ?ancestors)]
    db
    rules))
