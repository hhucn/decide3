(ns decide.client.router
  (:require
    [cljs.spec.alpha :as s]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.guardrails.core :refer [>def >defn- >defn => |]]
    [decide.routes :as routes]
    [reitit.coercion.spec :as rc.spec]
    [reitit.frontend :as rf]
    [reitit.frontend.easy :as rfe]
    [taoensso.timbre :as log]))

(>defn fill-segment-parameters [segments parameters]
  [::routes/segment (s/map-of keyword? any?) => ::routes/filled-segment]
  (mapv
    (fn [segment]
      (if (keyword? segment)
        (get parameters segment)
        segment))
    segments))

(defn on-navigate [app match]
  (let [segment-route (get-in match [:data :segment-route])
        parameters (get-in match [:parameters :path])]
    (cond
      segment-route (dr/change-route! app (fill-segment-parameters segment-route parameters) parameters)
      :else (log/warn "Empty segment! Match:" match))))

(defn start! [app]
  (let [router (rf/router routes/all-routes
                 {:syntax :bracket
                  :data {:coercion rc.spec/coercion}})]
    (rfe/start! router (partial on-navigate app)
      {:use-fragment false})))
