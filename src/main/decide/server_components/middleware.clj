(ns decide.server-components.middleware
  (:require
    [com.fulcrologic.fulcro.algorithms.denormalize :as dn]
    [com.fulcrologic.fulcro.algorithms.server-render :as ssr]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.dom-server :as dom]
    [com.fulcrologic.fulcro.server.api-middleware :refer [handle-api-request
                                                          wrap-transit-params
                                                          wrap-transit-response]]
    [decide.models.authorization :as auth]
    [decide.server-components.config :refer [config]]
    [decide.server-components.pathom :refer [parser]]
    [decide.ui.pages.splash :as splash]
    [decide.ui.theming.styles :as styles]
    [garden.core :as garden]
    [hiccup.page :refer [html5 include-js include-css]]
    [mount.core :refer [defstate]]
    [ring.middleware.defaults :refer [wrap-defaults]]
    [ring.middleware.gzip :refer [wrap-gzip]]
    [ring.middleware.session.cookie :refer [cookie-store]]
    [ring.util.response :as resp :refer [response file-response resource-response]]
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
          (parser {:ring/request request} tx)))
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
  ([csrf-token] (index csrf-token nil nil))
  ([csrf-token initial-state-script] (index csrf-token initial-state-script nil))
  ([csrf-token initial-state-script initial-html]

   (html5
     [:html {:lang "de"}
      [:head
       [:title "decide"]
       [:meta {:charset "utf-8"}]
       [:meta {:name "viewport" :content "width=device-width, initial-scale=1, maximum-scale=5"}]

       [:meta {:name "mobile-web-app-capable" :content "yes"}]
       [:meta {:name "apple-mobile-web-app-capable" :content "yes"}]
       [:meta {:name "application-name" :content "decide"}]
       [:meta {:name "apple-mobile-web-app-title" :content "decide"}]
       [:meta {:name "theme-color" :content styles/primary}]
       [:meta {:name "msapplication-navbutton-color" :content styles/primary}]
       [:meta {:name "apple-mobile-web-app-status-bar-style" :content "default"}]

       initial-state-script

       [:link {:href "https://fonts.googleapis.com/css?family=Roboto:300,400,500,700&display=swap" :rel "stylesheet"}]
       [:script (str "var fulcro_network_csrf_token = '" csrf-token "';")]
       [:style (garden/css styles/body styles/splashscreen styles/sizing styles/address)]]
      [:body
       [:div#decide initial-html]
       (include-js "/js/main/main.js")]])))

(defn index-with-db [csrf-token normalized-db]
  (log/debug "Serving index.html")
  (let [initial-state-script (ssr/initial-state->script-tag normalized-db)]
    (index csrf-token initial-state-script nil)))

(defn index-with-credentials [csrf-token request]
  (let [initial-state (ssr/build-initial-state (parser {:ring/request request} (comp/get-query auth/Session)) auth/Session)]
    (index csrf-token (ssr/initial-state->script-tag initial-state)
      splash/splash)))

(defn ssr-html [csrf-token app normalized-db root-component-class]
  (log/debug "Serving index.html")
  (let [props (dn/db->tree (comp/get-query root-component-class) normalized-db normalized-db)
        root-factory (comp/factory root-component-class)]
    (index
      csrf-token
      (ssr/initial-state->script-tag normalized-db)
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
              :rel  "stylesheet"}]
      [:link {:rel "shortcut icon" :href "data:image/x-icon;," :type "image/x-icon"}]
      [:script (str "var fulcro_network_csrf_token = '" csrf-token "';")]
      [:link {:href "/css/core.css"
              :rel  "stylesheet"}]]
     [:body
      [:div#app]
      [:script {:src "workspaces/js/main.js"}]]]))

(defn wrap-html-routes [ring-handler]
  (fn [{:keys [uri anti-forgery-token] :as req}]
    (cond
      ;; See note above on the `wslive` function.
      (#{"/wslive.html"} uri)
      (-> (resp/response (wslive anti-forgery-token))
        (resp/content-type "text/html"))

      (or (#{"/" "/index.html"} uri)
        (not (#{"/api" "/wslive.html"} uri)))
      (-> (index-with-credentials anti-forgery-token req)
        (resp/response)
        (resp/content-type "text/html"))


      :else
      (ring-handler req))))

(defstate middleware
  :start
  (let [defaults-config (:ring.middleware/defaults-config config)
        legal-origins (get config :legal-origins #{"localhost"})]
    (-> not-found-handler
      (wrap-api "/api")
      wrap-transit-params
      wrap-transit-response
      (wrap-html-routes)
      ;; If you want to set something like session store, you'd do it against
      ;; the defaults-config here (which comes from an EDN file, so it can't have
      ;; code initialized).
      ;; E.g. (wrap-defaults (assoc-in defaults-config [:session :store] (my-store)))
      (wrap-defaults (assoc defaults-config :session {:store (cookie-store {:key (byte-array 16)})})) ;; TODO configure this, when operation gets out of demo phase
      wrap-gzip)))
