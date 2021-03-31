(ns decide.server-components.email
  (:require
    [clojure.core.async :as async]
    [decide.server-components.config :refer [config]]
    [mount.core :refer [defstate]]
    [postal.core :as postal]
    [chime.core :as chime]
    [taoensso.timbre :as log])
  (:import (java.time Instant Duration)))


(defn print-email [{:keys [from to subject body]}]
  (println
    (str "=================================\n"
         "From: " from "\n"
         "To: " to "\n"
         "Subject: " subject "\n"
      body "\n"
      "=================================")))

(defn dev-mailer []
  (let [chan (async/chan 10)]
    (async/go-loop []
      (when-let [v (async/<! chan)]
        (try
          (print-email v)
          (catch Exception e
            (log/error e)))
        (recur)))
    chan))

(defn close! [chan]
  (log/debug "Stopping mailer...")
  (async/close! chan)
  (log/debug "Stopped mailer."))

(defstate mailer-chan
  :start
  (let [chan (async/chan 10)
        send-mail! (fn send-mail! [msg] (postal/send-message (get config :postal/email-config) msg))]
    (async/go-loop []
      (when-let [msg (async/<! chan)]
        (async/<!
          (async/thread
            (try
              (send-mail! msg)
              (catch Exception e
                (log/error e)))))
        (recur)))
    chan)
  :stop
  (close! mailer-chan))


