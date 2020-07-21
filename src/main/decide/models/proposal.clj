(ns decide.models.proposal)

(def schema
  [{:db/ident       :proposal/id
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity
    :db/valueType   :db.type/string}

   {:db/ident       :proposal/title
    :db/doc         "The short catchy title of a proposal."
    :db/cardinality :db.cardinality/one
    ; :db/fulltext    true
    :db/valueType   :db.type/string}

   {:db/ident       :proposal/body
    :db/doc         "A descriptive body of the proposal."
    :db/cardinality :db.cardinality/one
    ; :db/fulltext    true
    :db/valueType   :db.type/string}

   {:db/ident       :proposal/created
    :db/doc         "When the proposal was created."
    :db/cardinality :db.cardinality/one
    :db/valueType   :db.type/instant}

   {:db/ident       :proposal/original-author
    :db/doc         "The user who proposed the proposal."
    :db/cardinality :db.cardinality/one
    :db/valueType   :db.type/ref}

   {:db/ident       :proposal/parents
    :db/doc         "≥0 parent proposals from which the proposal is derived."
    :db/cardinality :db.cardinality/many
    :db/valueType   :db.type/ref}

   {:db/ident       :proposal/opinions
    :db/cardinality :db.cardinality/many
    :db/valueType   :db.type/ref
    :db/isComponent true}])
