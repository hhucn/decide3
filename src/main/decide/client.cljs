(ns decide.client
  (:require
    [com.fulcrologic.fulcro.algorithms.merge :as mrg]
    [com.fulcrologic.fulcro.algorithms.server-render :as ssr]
    [com.fulcrologic.fulcro.algorithms.timbre-support :refer [console-appender prefix-output-fn]]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.raw.components :as rc]
    [com.fulcrologic.fulcro.react.error-boundaries :as eb]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [decide.application :refer [SPA]]
    [decide.client.router :as routing]
    [decide.models.authorization :as auth]
    [decide.models.user :as-alias user]
    [decide.ui.root :as root]
    [taoensso.timbre :as log]))

(defn ^:export refresh []
  (log/info "Hot code Remount")
  #_(comp/refresh-dynamic-queries! SPA)
  (app/mount! SPA root/Root "decide" {:initialize-state? false}))

(defn initialize-routers! [app]
  (let [routers (dr/all-reachable-routers (app/current-state app) (app/root-class app))
        merge-initial-state (fn [state component] (mrg/merge-component state component (rc/get-initial-state component {})))]
    (swap! (::app/state-atom app)
      (fn [state] (reduce merge-initial-state state routers)))
    (dr/initialize! SPA)))

(defn ^:export init []
  (let [db (ssr/get-SSR-initial-state)]
    (log/merge-config!
      {:output-fn prefix-output-fn
       :appenders {:console (console-appender)}})
    (log/info "Application starting.")
    (app/set-root! SPA root/Root {:initialize-state? true})

    (set! eb/*render-error*
      (fn [this cause]
        (dom/div
          (dom/h2 :.header " Unexpected Error ")
          (dom/p "An error occurred while rendering the user interface. ")
          (dom/p (str cause))
          (when goog.DEBUG
            (dom/button {:onClick (fn []
                                    (comp/set-state! this {:error false
                                                           :cause nil}))} " Dev Mode: Retry rendering ")))))

    (log/trace "Swap in server provided state")
    (swap! (::app/state-atom SPA) merge db)

    (log/trace "Initialize client routing")
    (initialize-routers! SPA)
    (routing/start! SPA)

    ;; This makes the app start without a flicker of not-logged in state.
    ;; Does this hurt performance a lot? Think about that...
    (df/load! SPA ::user/current-session auth/Session
      {:target [:root/current-session]
       :post-action
       (fn [_]
         (app/mount! SPA root/Root "decide"
           {:initialize-state? false
            :hydrate false}))})))