(ns decide.application
  (:require
    [com.fulcrologic.fulcro.application :as app]
    #?@(:cljs [[com.fulcrologic.fulcro.networking.http-remote :as net]
               [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
               [com.fulcrologic.fulcro.algorithms.merge :as mrg]
               [decide.ui.login :as login]
               [decide.ui.main-app :as todo-app]
               [decide.routing :as routing]])
    [taoensso.timbre :as log]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro-css.css :as css]
    [com.fulcrologic.fulcro.algorithms.server-render :as ssr]))


#?(:cljs
   (def secured-request-middleware
     ;; The CSRF token is embedded via server_components/html.clj
     (->
       (net/wrap-csrf-token (or js/fulcro_network_csrf_token "TOKEN-NOT-IN-HTML!"))
       (net/wrap-fulcro-request))))

#?(:cljs
   (defn client-did-mount [app]
     (routing/start-history! app)
     (routing/start!)
     (let [{:decide.api.user/keys [current-session]} (ssr/get-SSR-initial-state)]
       (mrg/merge-component! app login/Session current-session :replace [:decide.api.user/current-session]))
     (let [logged-in? (get-in (app/current-state app) (into (comp/get-ident login/Session nil) [:session/valid?]))]
       (when-not logged-in?
         (comp/transact! app [(routing/route-to {:path (dr/path-to login/LoginPage)})])))))


(defonce SPA (app/fulcro-app
               #?(:cljs {:client-did-mount client-did-mount
                         :remotes          {:remote (net/fulcro-http-remote {:url                "/api"
                                                                             :request-middleware secured-request-middleware})}
                         :props-middleware (comp/wrap-update-extra-props
                                             (fn [cls extra-props]
                                               (merge extra-props (css/get-classnames cls))))})))