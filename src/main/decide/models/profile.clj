(ns decide.models.profile
  (:require
    [clojure.string :as str]
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [datahike.core :as d.core]
    [datahike.api :as d]
    [ghostwheel.core :refer [>defn >defn- => | ? <-]]
    [clojure.spec.alpha :as s]
    [taoensso.timbre :as log]))

(def not-blank-string (s/and string? (complement str/blank?)))

(def schema
  [{:db/ident       :profile/nickname
    :db/doc         "The unique nickname for a profile"
    :db/unique      :db.unique/identity
    :db/cardinality :db.cardinality/one
    :db/valueType   :db.type/string}

   {:db/ident       :profile/name
    :db/doc         "A (not unique) name to display for the public."
    :db/cardinality :db.cardinality/one
    :db/valueType   :db.type/string}

   {:db/ident       :profile/identities
    :db/cardinality :db.cardinality/many
    :db/valueType   :db.type/ref}])

(s/def :profile/nickname not-blank-string)
(s/def :profile/name not-blank-string)

(defresolver resolve-profile [{:keys [db]} {:keys [profile/nickname]}]
  {::pc/input #{:profile/nickname}
   ::pc/output [:profile/name]}
  {:profile/name
   (d/q '[:find ?name .
          :in $ ?nickname
          :where
          [?e :profile/nickname ?nickname]
          [?e :profile/name ?name]]
     db nickname)})

(def resolvers [resolve-profile])