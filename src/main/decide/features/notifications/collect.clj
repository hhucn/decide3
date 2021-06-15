(ns decide.features.notifications.collect
  (:require
    [clojure.spec.alpha :as s]
    [datahike.api :as d]
    [decide.models.argument :as argument]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.server-components.database :refer [conn]])
  (:import (java.time Instant)
           (java.util Date)
           (java.time.temporal ChronoUnit)))

(s/def :event/what #{:event.type/new-proposal :event.type/new-argument})
(s/def :event/when inst?)
(s/def :event/who :decide.models.user/id)
(s/def :event/tx pos-int?)
(s/def :event/eid pos-int?)
(s/def ::event (s/keys
                 :req [:event/what
                       :event/when
                       :event/tx]
                 :opt [:event/who]))

(defn yesterday []
  (Date/from (.minus (Instant/now) 24 ChronoUnit/HOURS)))

(defn minutes-ago [minutes]
  (Date/from (.minus (Instant/now) minutes ChronoUnit/MINUTES)))

(defn to-event [{:keys [entity transaction]}]
  #:event{:what (if (:decide.models.argument/id entity) :event.type/new-argument :event.type/new-proposal)
          :eid (:db/id entity)
          :when (:db/txInstant transaction)
          :tx (:db/id transaction)})

(defn new-things [db]
  (->> db
    (d/q '[:find (pull ?e [:db/id
                           :decide.models.argument/id
                           :decide.models.argument/content
                           :decide.models.proposal/id
                           ::proposal/title])
           (pull ?tx [:db/id :db/txInstant :db/txUser])
           :keys entity transaction
           :where
           [?e ?a _ ?tx]
           [(contains?
              #{::argument/id ::proposal/id}
              ?a)]])
    (map to-event)))

;; region enhance-with-slug
(defmulti enhance-with-slug (fn [_db event] (:event/what event)))

(defmethod enhance-with-slug :event.type/new-argument
  [db {:event/keys [eid] :as event}]
  (let [slug (-> (d/pull db [{::proposal/_arguments [{::process/_proposals [::process/slug]}]}] eid)
               ::proposal/_arguments first ::process/_proposals ::process/slug)]
    (assoc event ::process/slug slug)))

(defmethod enhance-with-slug :event.type/new-proposal
  [db {:event/keys [eid] :as event}]
  (let [slug (get-in (d/pull db [{::process/_proposals [::process/slug]}] eid)
               [::process/_proposals ::process/slug])]
    (assoc event ::process/slug slug)))
;; endregion

(defn events-by-slug [db since]
  (->>
    (d/since db since)
    new-things
    (map (partial enhance-with-slug db))
    (group-by ::process/slug)))