(ns decide.features.notifications.collect
  (:require
    [clojure.spec.alpha :as s]
    [com.fulcrologic.guardrails.core :refer [>defn => ? | <-]]
    [datahike.api :as d]
    [datahike.core :as d.core]
    [decide.models.argumentation.database :as argumentation.db]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal])
  (:import (java.time Instant)
           (java.util Date)
           (java.time.temporal ChronoUnit)))

(s/def ::eid pos-int?)
(s/def :event/what #{:event.type/new-proposal :event.type/new-argument})
(s/def :event/when inst?)
(s/def :event/who ::eid)
(s/def :event/tx ::eid)
(s/def :event/eid ::eid)
(s/def :event/proposal ::eid)
(s/def :event/process ::eid)
(s/def ::event (s/keys
                 :req [:event/what
                       :event/when
                       :event/who
                       :event/tx
                       :event/process]
                 :opt [:event/proposal]))

(defn yesterday []
  (Date/from (.minus (Instant/now) 24 ChronoUnit/HOURS)))

(defn minutes-ago [minutes]
  (Date/from (.minus (Instant/now) minutes ChronoUnit/MINUTES)))

(defn to-event [{:keys [entity tx]}]
  #:event{:what (if (:argument/id entity) :event.type/new-argument :event.type/new-proposal)
          :eid (:db/id entity)
          :when (:db/txInstant tx)
          :tx (:db/id tx)})

(defn ^:deprecated new-things [db]
  (->> db
    (d/q '[:find (pull ?e [:db/id
                           :argument/id
                           :decide.models.proposal/id
                           ::proposal/title])
           (pull ?tx [:db/id :db/txInstant :db/txUser])
           :keys entity transaction
           :where
           [?e ?a _ ?tx]
           [(contains?
              #{:argument/id ::proposal/id}
              ?a)]])
    (map to-event)))

(>defn get-new-argument-events [db time-point]
  [d.core/db? inst? => (s/coll-of ::event)]
  (d/q
    '[:find ?what ?when ?e ?tx ?who ?proposal ?process
      :keys event/what event/when event/eid event/tx event/who event/proposal event/process
      :in $ $history %
      :where
      [(ground :event.type/new-argument) ?what]
      [$history ?e :argument/id _ ?tx true]                 ; don't include retractions
      [?tx :db/txInstant ?when]
      [?e :argument/premise ?premise]
      [?premise :author ?who]
      (belongs-to-proposal ?e ?proposal)
      [?process ::process/proposals ?proposal]]
    db (d/since db time-point) argumentation.db/argumentation-rules))


(>defn get-new-proposal-events [db time-point]
  [d.core/db? inst? => (s/coll-of ::event)]
  (d/q '[:find ?what ?when ?e ?tx ?who ?process
         :keys event/what event/when event/eid event/tx event/who event/process
         :where
         [(ground :event.type/new-proposal) ?what]
         [?e ::proposal/id _ ?tx true]                      ; don't include retractions
         [?tx :db/txInstant ?when]
         [?e ::proposal/original-author ?who]
         [?process ::process/proposals ?e]]
    (d/since db time-point)))

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

(defn all-events [db time-point]
  [d.core/db? inst? => (s/coll-of ::event :distinct true)]
  (let [event-extractors [get-new-argument-events get-new-proposal-events]
        execute! (->> event-extractors
                   (map (partial comp future-call))
                   (apply juxt))]
    (mapcat deref (execute! db time-point))))

(defn ^:deprecated events-by-slug [db since]
  (->>
    (d/since db since)
    new-things
    (map (partial enhance-with-slug db))
    (group-by ::process/slug)))