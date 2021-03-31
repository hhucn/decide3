(ns decide.features.notifications.format-email
  (:require
    [clojure.string :as str]
    [datahike.api :as d]
    [decide.models.argument :as argument]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]
    [hiccup.page :refer [html5]]
    [taoensso.timbre :as log]))

(defmulti format-event (fn [_db event] (:event/what event)))
(defmethod format-event :event.type/new-proposal
  [db event]
  (let [{::proposal/keys [title]} (d/pull db [::proposal/title] (:event/ref event))]
    [:li {} (str "New proposal: " title)]))

(defmethod format-event :event.type/new-argument
  [db event]
  (let [{::argument/keys [content]} (d/pull db [::argument/content] (:event/ref event))]
    [:li {} (str "New argument: " content)]))

(defmethod format-event :default [_ event] (str event))

(defn format-payload [db payload]
  (for [[slug events] payload]
    [:div
      [:p.list-header slug]
      [:ul
       (map (partial format-event db) events)]]))

(defn format-email [db {::user/keys [display-name]} payload]
  (html5
    [:html
     [:body
      [:p "Hi " display-name ",\n\nthere is something new on decide!\n"]
      (format-payload db payload)]]))

(defn make-message [db user payload]
  {:to (if (str/includes? (::user/email user) "@")
         (::user/email user)
         "ebbinghaus@hhu.de")
   :from "decide <decide@hhu.de>"
   :subject "News from decide!"
   :body [{:content (format-email db user payload)
           :type "text/html"}]})
