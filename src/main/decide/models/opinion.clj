(ns decide.models.opinion
  (:require
    [clojure.spec.alpha :as s]
    [datahike.api :as d]
    [datahike.core :as d.core]
    [ghostwheel.core :refer [>defn =>]]))

(def schema [{:db/ident       :profile/opinions
              :db/cardinality :db.cardinality/many
              :db/valueType   :db.type/ref
              :db/isComponent true}

             {:db/ident       :opinion/value
              :db/doc         "Value of alignment of the opinion. 0 is neutral. Should be -1, 0 or +1 for now."
              :db/cardinality :db.cardinality/one
              :db/valueType   :db.type/long}])

(s/def :opinion/value #{-1 0 +1})

(>defn set-opinion! [conn profile-nickname proposal-id opinion-value]
  [d.core/conn? :profile/nickname :proposal/id :opinion/value => map?]
  (d/transact conn
    [[:db/add [:proposal/id proposal-id] :proposal/opinions "temp"]
     [:db/add [:profile/nickname profile-nickname] :profile/opinions "temp"]
     {:db/id         "temp"
      :opinion/value opinion-value}]))