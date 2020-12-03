(ns decide.client
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [taoensso.timbre :as log]
    [decide.application :refer [SPA]]
    [decide.ui.root :as root]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [decide.routing :as routing]))

(defn ^:export refresh []
  (log/info "Hot code Remount")
  (comp/refresh-dynamic-queries! SPA)
  (app/mount! SPA root/Root "decide" {:initialize-state? false}))

(defn ^:export init []
  (log/info "Application starting.")
  (app/set-root! SPA root/Root {:initialize-state? true})
  (routing/start-history! SPA)
  (routing/start!)
  (dr/initialize! SPA)
  (app/mount! SPA root/Root "decide"
    {:initialize-state? false
     :hydrate           false}))