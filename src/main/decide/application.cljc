(ns decide.application
  (:require
    #?@(:cljs [[com.fulcrologic.fulcro.algorithms.merge :as mrg]
               [com.fulcrologic.fulcro.algorithms.server-render :as ssr]
               [com.fulcrologic.fulcro.application :as app]
               [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
               [decide.routing :as routing]
               [decide.ui.login :as login]])
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.rad.application :as rad-app]
    [com.fulcrologic.fulcro-css.css :as css]
    [decide.models.user :as user]))

(defn client-did-mount [app]
  #?(:cljs
     (let [{::user/keys [current-session]} (ssr/get-SSR-initial-state)
           logged-in? (get-in (app/current-state app) (into (comp/get-ident user/Session nil) [:session/valid?]))]
       (mrg/merge-component! app user/Session current-session :replace [:current-session])
       #_(when-not logged-in?
           (comp/transact! app [(routing/route-to {:path (dr/path-to login/LoginPage)})])))))

(defonce SPA
  (rad-app/fulcro-rad-app
    {;:client-did-mount client-did-mount
     :props-middleware (comp/wrap-update-extra-props
                         (fn [cls extra-props]
                           (merge extra-props (css/get-classnames cls))))}))