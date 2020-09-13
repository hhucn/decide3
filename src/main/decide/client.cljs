(ns decide.client
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [taoensso.timbre :as log]
    [decide.application :refer [SPA]]
    [decide.ui.root :as root]))

(defn ^:export refresh []
  (log/info "Hot code Remount")
  (app/mount! SPA root/Root "decide" {:initialize-state? false}))

(defn ^:export init []
  (log/info "Application starting.")
  (app/mount! SPA root/Root "decide"))