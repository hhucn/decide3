(ns decide.models.user
  (:require
    [datahike.core :as d.core]
    [datahike.api :as d]
    [ghostwheel.core :refer [>defn >defn- => | ? <-]]
    [clojure.spec.alpha :as s]
    [buddy.hashers :as hs]))

(s/def ::id uuid?)
(s/def ::email string?)
(s/def ::password string?)

(def schema [{:db/ident       ::id
              :db/doc         "The id of a user"
              :db/unique      :db.unique/identity
              :db/cardinality :db.cardinality/one
              :db/valueType   :db.type/uuid}

             {:db/ident       ::password
              :db/doc         "Password of a user"
              :db/cardinality :db.cardinality/one
              :db/valueType   :db.type/string
              :db/noHistory   true}

             {:db/ident       ::email
              :db/unique      :db.unique/identity
              :db/cardinality :db.cardinality/one
              :db/valueType   :db.type/string}])

(>defn hash-password [password]
  [::password => string?]
  (hs/derive password))

(>defn email-in-db?
  "True if email is already in the db."
  [db email]
  [d.core/db? ::email => boolean?]
  (not (empty? (d/q '[:find ?e
                      :in $ ?email
                      :where [?e ::email ?email]]
                 db email))))

(>defn get-by-email
  ([db email]
   [d.core/db? ::email => map?]
   (get-by-email db email [::id ::email]))
  ([db email query]
   [d.core/db? ::email any? => map?]
   (d/pull db query [::email email])))

(>defn password-valid? [user attempt]
  [(s/keys :req [::password]) ::password => boolean?]
  (let [{::keys [password]} user]
    (hs/check attempt password)))


