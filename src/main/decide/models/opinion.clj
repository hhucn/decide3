(ns decide.models.opinion
  (:require
    [clojure.spec.alpha :as s]
    [datahike.api :as d]
    [datahike.core :as d.core]
    [ghostwheel.core :refer [>defn =>]]))

(def schema [{:db/ident       :opinion/user
              :db/cardinality :db.cardinality/one
              :db/valueType   :db.type/ref}

             {:db/ident       :opinion/value
              :db/doc         "Value of alignment of the opinion. 0 is neutral. Should be -1, 0 or +1 for now."
              :db/cardinality :db.cardinality/one
              :db/valueType   :db.type/long}])

(s/def :opinion/value #{-1 0 +1})

(>defn set-opinion [conn user-id proposal-id opinion-value]
  [d.core/conn? :user/id :proposal/id :opinion/value => map?]
  (d/transact conn
    [[:db/add [:proposal/id proposal-id] :proposal/opinions "temp"]
     {:db/id         "temp"
      :opinion/user  [:user/id user-id]
      :opinion/value opinion-value}]))