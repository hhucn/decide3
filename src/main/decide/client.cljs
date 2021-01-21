(ns decide.client
  (:require
    [com.fulcrologic.fulcro.algorithms.timbre-support :refer [console-appender prefix-output-fn]]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [decide.application :refer [SPA]]
    [decide.models.user :as user]
    [decide.routing :as routing]
    [decide.ui.root :as root]
    [taoensso.timbre :as log]))

(defn ^:export refresh []
  (log/info "Hot code Remount")
  (comp/refresh-dynamic-queries! SPA)
  (app/mount! SPA root/Root "decide" {:initialize-state? false}))

(defn ^:export init []
  (log/merge-config! {:output-fn prefix-output-fn
                      :appenders {:console (console-appender)}})
  (log/info "Application starting.")
  (app/set-root! SPA root/Root {:initialize-state? true})
  (routing/start-history! SPA)
  (dr/initialize! SPA)

  (df/load! SPA ::user/current-session user/Session {:target [:root/current-session]})
  (swap! (::app/state-atom SPA) update :process-context dissoc nil)

  (routing/start!)
  (app/mount! SPA root/Root "decide"
    {:initialize-state? false
     :hydrate false}))