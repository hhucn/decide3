(ns decide.models.argumentation.database)

(def argumentation-rules
  '[;; TODO This doesn't work
    [(sub-argument ?argument ?sub-argument)
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

    [(belongs-to-proposal ?argument ?proposal)
     [?proposal :decide.models.proposal/arguments ?argument]]

    [(belongs-to-proposal ?argument ?proposal)
     (sub-argument ?parent ?argument)
     (belongs-to-proposal ?parent ?proposal)]])
