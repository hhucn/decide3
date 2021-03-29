(ns decide.schema)

(def schema
  [{:db/ident ::author
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}])

(def rules
  '[
    ;; ancestor rules
    [(child ?child ?parent)
     [?child :decide.models.proposal/parents ?parent]]

    [(parent ?parent ?child)
     (child ?child ?parent)]

    [(descendant ?this ?other)
     (child ?this ?other)]
    [(descendant ?this ?other)
     (child ?this ?parent)
     (descendant ?parent ?other)]

    [(ancestor ?this ?other)
     (descendant ?other ?this)]


    ;; approve rules
    [(opinion ?user ?proposal ?value)
     [?user :decide.models.user/opinions ?opinion]
     [?proposal :decide.models.proposal/opinions ?opinion]
     [?opinion :decide.models.opinion/value ?value]]

    [(approves? ?user ?proposal)
     (opinion ?user ?proposal +1)]

    [(rejects? ?user ?proposal)
     (opinion ?user ?proposal -1)]

    [(undecided? ?user ?proposal)
     (or-join [?user ?proposal]
       (not
         [?user :decide.models.user/opinions ?opinion]
         [?proposal :decide.models.proposal/opinions ?opinion])
       (opinion ?user ?proposal 0))]])