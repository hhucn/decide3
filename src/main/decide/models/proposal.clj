(ns decide.models.proposal
  (:require
    [datahike.api :as d]
    [datahike.core :as d.core]
    [ghostwheel.core :refer [>defn >defn- => | ? <-]]
    [clojure.spec.alpha :as s]
    [decide.models.user :as user]))

(def schema
  [{:db/ident       ::id
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity
    :db/valueType   :db.type/string}
   {:db/ident       ::title
    :db/doc         "The short catchy title of a proposal."
    :db/cardinality :db.cardinality/one
    :db/valueType   :db.type/string}

   {:db/ident       ::body
    :db/doc         "A descriptive body of the proposal."
    :db/cardinality :db.cardinality/one
    :db/valueType   :db.type/string}

   {:db/ident       ::created
    :db/doc         "When the proposal was created."
    :db/cardinality :db.cardinality/one
    :db/valueType   :db.type/instant}

   {:db/ident       ::original-author
    :db/doc         "When the proposal was created."
    :db/cardinality :db.cardinality/one
    :db/valueType   :db.type/ref}

   {:db/ident       ::parents
    :db/doc "≥0 parent proposals from which the proposal is derived."
    :db/cardinality :db.cardinality/many
    :db/valueType   :db.type/ref}])
