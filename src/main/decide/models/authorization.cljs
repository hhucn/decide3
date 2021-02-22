(ns decide.models.authorization
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [decide.models.user :as user]
    [goog.net.cookies]))

(defsc Session [_ _]
  {:query [:session/valid?
           {:user (comp/get-query user/User)}
           ::user/id]
   :initial-state {:session/valid? false}})

(def current-session-link
  [:root/current-session '_])

