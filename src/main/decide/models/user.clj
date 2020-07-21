(ns decide.models.user
  (:require
    [datahike.core :as d.core]
    [datahike.api :as d]
    [ghostwheel.core :refer [>defn >defn- => | ? <-]]
    [clojure.spec.alpha :as s]
    [buddy.hashers :as hs]))

(def schema [{:db/ident       :user/id
              :db/doc         "The id of a user"
              :db/unique      :db.unique/identity
              :db/cardinality :db.cardinality/one
              :db/valueType   :db.type/uuid}

             {:db/ident       :user/password
              :db/doc         "Password of a user"
              :db/cardinality :db.cardinality/one
              :db/valueType   :db.type/string
              :db/noHistory   true}

             {:db/ident       :user/email
              :db/unique      :db.unique/identity
              :db/cardinality :db.cardinality/one
              :db/valueType   :db.type/string}])

(s/def :user/id uuid?)
(s/def :user/email string?)
(s/def :user/password string?)

(>defn hash-password [password]
  [:user/password => string?]
  (hs/derive password))

(>defn email-in-db?
  "True if email is already in the db."
  [db email]
  [d.core/db? :user/email => boolean?]
  (not (empty? (d/q '[:find ?e
                      :in $ ?email
                      :where [?e :user/email ?email]]
                 db email))))

(>defn get-by-email
  ([db email]
   [d.core/db? :user/email => map?]
   (get-by-email db email [:user/id :user/email]))
  ([db email query]
   [d.core/db? :user/email any? => map?]
   (d/pull db query [:user/email email])))

(>defn password-valid? [user attempt]
  [(s/keys :req [:user/password]) :user/password => boolean?]
  (let [{:user/keys [password]} user]
    (hs/check attempt password)))


