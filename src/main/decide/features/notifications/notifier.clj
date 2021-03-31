(ns decide.features.notifications.notifier
  (:require
    [clojure.core.async :as async]
    [datahike.api :as d]
    [mount.core :refer [defstate]]
    [chime.core :as chime]
    [decide.features.notifications.collect :as collect]
    [decide.features.notifications.format-email :as format]
    [decide.models.process :as process]
    [decide.models.user :as user]
    [decide.server-components.database :refer [conn]]
    [decide.server-components.email :refer [mailer-chan]]
    [taoensso.timbre :as log])
  (:import (java.time Instant Duration)))

(defn get-all-users-with-emails [db]
  (d/q '[:find [(pull ?user [::user/email ::user/display-name
                             {::process/_participants [::process/slug]}]) ...]
         :where
         [?user ::user/email]
         [?process ::process/participants ?user]]
    db))

(defn notify-user! [db user events]
  (let [process-slugs (map ::process/slug (get user ::process/_participants []))
        payload (select-keys events process-slugs)]
    (when-not (empty? payload)
      (log/debug "Notify " (:decide.models.user/email user))
      (log/spy :info payload)
      (async/put! mailer-chan
        (format/make-message db user payload)))))

(defn send-email-notifications! [db since]
  (let [events (collect/events-by-slug db since)]
    (doseq [user (get-all-users-with-emails db)]
      (notify-user! db user events))))

(defstate notifier
  :start
  (chime/chime-at
    (chime/periodic-seq (Instant/now) (Duration/ofMinutes 60))
    (fn [time]
      (log/info "Notify!")
      (send-email-notifications! (d/db conn) (collect/minutes-ago 60))))
  :stop
  (.close notifier))