(ns decide.application
  (:require
    [com.fulcrologic.fulcro.application :as app]
    #?@(:cljs [[com.fulcrologic.fulcro.networking.http-remote :as net]
               [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
               [com.fulcrologic.fulcro.algorithms.merge :as mrg]
               [com.fulcrologic.fulcro.algorithms.timbre-support :refer [console-appender prefix-output-fn]]
               [decide.ui.login :as login]
               [decide.ui.main-app :as todo-app]
               [decide.routing :as routing]])
    [decide.models.user :as user]
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
     (let [{::user/keys [current-session]} (ssr/get-SSR-initial-state)]
       (mrg/merge-component! app user/Session current-session :replace [:current-session]))
     (let [logged-in? (get-in (app/current-state app) (into (comp/get-ident user/Session nil) [:session/valid?]))]
       (when-not logged-in?
         (comp/transact! app [(routing/route-to {:path (dr/path-to login/LoginPage)})])))))

#?(:cljs
   (defn mutation?
     [body]
     (and
       (map? body)
       (-> body keys first symbol?))))

#?(:cljs
   (defn mutation-error? [{:keys [body]}]
     (let [is-mutation? (mutation? body)]
       (if is-mutation?
         (let [mutation-sym (-> body keys first)
               response-error? (-> body mutation-sym :server/error?)
               pathom-error (-> body mutation-sym :com.wsscode.pathom.core/reader-error)]
           (log/info "Result body: " body)
           (boolean (or response-error? pathom-error)))
         false))))

(defonce SPA
  (app/fulcro-app
    #?(:cljs {:client-will-mount (fn [app]
                                   (log/merge-config! {:output-fn prefix-output-fn
                                                       :appenders {:console (console-appender)}})
                                   (routing/start-history! app)
                                   (routing/start!))
              :client-did-mount  client-did-mount
              :remotes           {:remote (net/fulcro-http-remote {:url                "/api"
                                                                   :request-middleware secured-request-middleware})}
              :remote-error?     (some-fn app/default-remote-error?)
              :props-middleware  (comp/wrap-update-extra-props
                                   (fn [cls extra-props]
                                     (merge extra-props (css/get-classnames cls))))})))