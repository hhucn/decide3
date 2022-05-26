(ns decide.server-components.middleware
  (:require
   [com.fulcrologic.fulcro-i18n.i18n :as i18n]
   [com.fulcrologic.fulcro.algorithms.denormalize :as dn]
   [com.fulcrologic.fulcro.algorithms.server-render :as ssr]
   [com.fulcrologic.fulcro.components :as comp]
   [com.fulcrologic.fulcro.dom-server :as dom]
   [com.fulcrologic.fulcro.server.api-middleware :refer [handle-api-request
                                                         wrap-transit-params
                                                         wrap-transit-response]]
   [com.wsscode.pathom3.connect.operation.transit :as pcot]
   [decide.models.authorization :as auth]
   [decide.server-components.config :refer [config]]
   [decide.server-components.database :refer [conn]]
   [decide.server-components.pathom3 :as component.pathom3]
   [decide.ui.pages.splash :as splash]
   [decide.ui.theming.styles :as styles]
   [decide.utils.header :as utils.header]
   [garden.core :as garden]
   [hiccup.page :refer [html5 include-js]]
   [mount.core :refer [defstate]]
   [ring.middleware.defaults :refer [wrap-defaults]]
   [ring.middleware.gzip :refer [wrap-gzip]]
   [ring.middleware.resource :refer [wrap-resource]]
   [ring.middleware.session.cookie :refer [cookie-store]]
   [ring.util.response :as resp]
   [taoensso.timbre :as log]))


(def ^:private not-found-handler
  (fn [req]
    {:status 404
     :headers {"Content-Type" "text/plain"}
     :body "NOPE"}))


(defn wrap-api [handler uri]
  (fn [request]
    (if (= uri (:uri request))
      (handle-api-request
        (:transit-params request)
        (fn request-handler [tx]
          (component.pathom3/processor {:ring/request request} tx)))
      (handler request))))


(defmacro link-to-icon [size]
  (let [url (str "/assets/icons/icon-" size "x" size ".png")
        dimensions (str size "x" size)]
    `(do
       [:link {:rel "icon" :type "image/png" :sizes ~dimensions :href ~url}]
       [:link {:rel "apple-touch-icon" :type "image/png" :sizes ~dimensions :href ~url}])))

;; ================================================================================
;; Dynamically generated HTML. We do this so we can safely embed the CSRF token
;; in a js var for use by the client.
;; ================================================================================
(defn index
  ([csrf-token script-manifest] (index csrf-token script-manifest nil nil))
  ([csrf-token script-manifest initial-state-script] (index csrf-token script-manifest initial-state-script nil))
  ([csrf-token script-manifest initial-db initial-html]
   (html5
     [:html {:lang "de"}
      [:head
       [:title "decide"]
       [:meta {:charset "utf-8"}]
       [:meta {:name "viewport" :content "width=device-width, initial-scale=1, maximum-scale=5"}]
       [:link {:rel "icon" :type "image/svg+xml" :href (if (:dev? config) "/assets/icons/favicon_dev.svg" "/assets/icons/favicon.svg")}]

       [:meta {:name "mobile-web-app-capable" :content "yes"}]
       [:meta {:name "apple-mobile-web-app-capable" :content "yes"}]
       [:meta {:name "application-name" :content "decide"}]
       [:meta {:name "apple-mobile-web-app-title" :content "decide"}]
       [:meta {:name "theme-color" :content styles/primary}]
       [:meta {:name "msapplication-navbutton-color" :content styles/primary}]
       [:meta {:name "apple-mobile-web-app-status-bar-style" :content "default"}]

       (ssr/initial-state->script-tag initial-db)

       [:link {:href "https://fonts.googleapis.com/css?family=Roboto:300,400,500,700&display=swap" :rel "stylesheet"}]
       [:script (str "var fulcro_network_csrf_token = '" csrf-token "';")]
       [:style (garden/css styles/body styles/splashscreen styles/sizing styles/address)]]
      [:body
       [:div#decide initial-html]
       (for [{:keys [output-name]} script-manifest
             :when output-name]
         (include-js (str "/js/main/" output-name)))]])))

; "?v=" js-hash

(comp/defsc Root [_ _]
  {:query [{::i18n/current-locale (comp/get-query i18n/Locale)}
           {:root/current-session (comp/get-query auth/Session)}]})

(defn- lang [request]
  (or
    (:user/language (auth/get-session-user @conn request))
    (-> request
      (get-in [:headers "accept-language"] "en")
      utils.header/preferred-language
      keyword)))

(def load-locale (memoize (partial i18n/load-locale "po-files")))

(defn index-with-credentials [csrf-token script-manifest request]
  (let [locale (or (load-locale (lang request)) {::i18n/locale :en})
        initial-state
        (->
          (comp/get-initial-state Root)
          (assoc ::i18n/current-locale locale)
          (ssr/build-initial-state Root))]
    (index csrf-token script-manifest initial-state splash/splash)))

(defn ssr-html [csrf-token app normalized-db root-component-class]
  (log/debug "Serving index.html")
  (let [props (dn/db->tree (comp/get-query root-component-class) normalized-db normalized-db)
        root-factory (comp/factory root-component-class)]
    (index
      csrf-token
      normalized-db
      (binding [comp/*app* app]
        (dom/render-to-str (root-factory props))))))

;; ================================================================================
;; Workspaces can be accessed via shadow's http server on http://localhost:8023/workspaces.html
;; but that will not allow full-stack fulcro cards to talk to your server. This
;; page embeds the CSRF token, and is at `/wslive.html` on your server (i.e. port 3000).
;; ================================================================================
(defn wslive [csrf-token]
  (log/debug "Serving wslive.html")
  (html5
    [:html {:lang "en"}
     [:head {:lang "en"}
      [:title "Workspaces"]
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"}]
      [:link {:href "https://cdnjs.cloudflare.com/ajax/libs/semantic-ui/2.4.1/semantic.min.css"
              :rel "stylesheet"}]
      [:link {:rel "shortcut icon" :href "data:image/x-icon;," :type "image/x-icon"}]
      [:script (str "var fulcro_network_csrf_token = '" csrf-token "';")]
      [:link {:href "/css/core.css"
              :rel "stylesheet"}]]
     [:body
      [:div#app]
      [:script {:src "workspaces/js/main.js"}]]]))

(defn wrap-html-routes [ring-handler script-manifest]
  (fn [{:keys [uri anti-forgery-token] :as req}]
    (cond
      ;; See note above on the `wslive` function.
      (#{"/wslive.html"} uri)
      (-> (resp/response (wslive anti-forgery-token))
        (resp/content-type "text/html"))

      (or (#{"/" "/index.html"} uri)
        (not (#{"/api" "/wslive.html"} uri)))
      (-> (index-with-credentials anti-forgery-token script-manifest req)
        (resp/response)
        (resp/content-type "text/html"))


      :else
      (ring-handler req))))

(defn get-key-from-string [^String cookie-store-key]
  {:pre [(= 15 (count cookie-store-key))]}
  (when-not (= 15 (count cookie-store-key))
    (throw (ex-info "The Cookie store secret key needs to be 16 bytes long!" {}))
    (System/exit -1))
  (.getBytes cookie-store-key))


(defstate middleware
  :start
  (let [defaults-config (:ring.middleware/defaults-config config)
        legal-origins (get config :legal-origins #{"localhost"})]
    (-> not-found-handler
      (wrap-api "/api")
      (wrap-transit-params {:opts {:handler pcot/read-handlers}})
      (wrap-transit-response {:opts {:handlers pcot/write-handlers}})
      (wrap-html-routes (:script-manifest config))
      ;; If you want to set something like session store, you'd do it against
      ;; the defaults-config here (which comes from an EDN file, so it can't have
      ;; code initialized).
      ;; E.g. (wrap-defaults (assoc-in defaults-config [:session :store] (my-store)))
      (wrap-resource "public")
      (wrap-defaults (assoc-in defaults-config [:session :store]
                       (cookie-store {:key (.getBytes ^String (:cookie-store-secret-key config))})))
      wrap-gzip)))
