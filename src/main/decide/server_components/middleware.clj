(ns decide.server-components.middleware
  (:require
    [decide.server-components.config :refer [config]]
    [decide.server-components.pathom :refer [parser *trace?*]]
    [mount.core :refer [defstate]]
    [com.fulcrologic.fulcro.server.api-middleware :refer [handle-api-request
                                                          wrap-transit-params
                                                          wrap-transit-response]]
    [com.fulcrologic.fulcro.algorithms.server-render :as ssr]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.dom-server :as dom]
    [com.fulcrologic.fulcro.algorithms.denormalize :as dn]
    [com.fulcrologic.fulcro.algorithms.normalize :as norm]
    [ring.middleware.defaults :refer [wrap-defaults]]
    [ring.middleware.gzip :refer [wrap-gzip]]
    [ring.util.response :refer [response file-response resource-response]]
    [ring.util.response :as resp]
    [hiccup.page :refer [html5 include-js include-css]]
    [taoensso.timbre :as log]
    [decide.application :refer [SPA]]))

(def ^:private not-found-handler
  (fn [req]
    {:status  404
     :headers {"Content-Type" "text/plain"}
     :body    "NOPE"}))


(defn wrap-api [handler uri]
  (binding [*trace?* (not (nil? (System/getProperty "trace")))]
    (fn [request]
      (if (= uri (:uri request))
        (handle-api-request
          (:transit-params request)
          (fn request-handler [tx]
            (parser {:ring/request request} tx)))
        (handler request)))))

(comp/defsc Session [_ _]
  {:query         [{:decide.api.user/current-session [:session/valid? :decide.models.user/id]}]
   :ident         (fn [] [:component/id :session])
   :initial-state {:decide.api.user/current-session {:session/valid?         false
                                                      :decide.models.user/id nil}}})
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
     [:html {:lang "en"}
      [:head
       [:title "decide"]
       [:meta {:charset "utf-8"}]
       [:link {:rel "icon" :type "image/png" :href "/assets/icons/favicon-16.png" :sizes "16x16"}]
       [:link {:rel "icon" :type "image/png" :href "/assets/icons/favicon-32.png" :sizes "32x32"}]
       [:link {:rel "icon" :type "image/png" :href "/assets/icons/favicon-48.png" :sizes "48x48"}]
       initial-state-script
       [:link {:rel "manifest" :href "/manifest.json"}]
       [:meta {:name "mobile-web-app-capable" :content "yes"}]
       [:meta {:name "apple-mobile-web-app-capable" :content "yes"}]
       [:meta {:name "application-name" :content "decide"}]
       [:meta {:name "apple-mobile-web-app-title" :content "decide"}]
       [:meta {:name "theme-color" :content "#d32f2f"}]
       [:meta {:name "msapplication-navbutton-color" :content "#d32f2f"}]
       [:meta {:name "apple-mobile-web-app-status-bar-style" :content "default"}]
       [:link {:rel "mask-icon" :sizes "any" :href "/assets/icons/T.svg" :color "#d32f2f"}]
       [:meta {:name "msapplication-starturl" :content "/app/home"}]
       [:meta {:name "viewport" :content "width=device-width, initial-scale=1, maximum-scale=5"}]
       (link-to-icon 72)
       (link-to-icon 96)
       (link-to-icon 128)
       (link-to-icon 144)
       (link-to-icon 152)
       (link-to-icon 192)
       (link-to-icon 384)
       (link-to-icon 512)

       [:link {:href "https://fonts.googleapis.com/css?family=Roboto:300,400,500,700&display=swap" :rel "stylesheet"}]
       [:script (str "var fulcro_network_csrf_token = '" csrf-token "';")]]
      [:body
       [:div#decide
        (or
          initial-html
          [:div {:style "background-color: #d32f2f; width: 100%;  height: 100%; margin: 0; position: absolute; top: 0; left: 0;
                      display: flex; align-items: center; justify-content: center;"}
           [:img {:src "/assets/decide.svg" :max-width "30%"}]])
        initial-html]
       (include-js "/js/main/main.js")]])))

(defn index-with-db [csrf-token normalized-db]
  (log/debug "Serving index.html")
  (let [initial-state-script (ssr/initial-state->script-tag normalized-db)]
    (index csrf-token initial-state-script nil)))

(defn index-with-credentials [csrf-token request]
  (let [initial-state (ssr/build-initial-state (parser {:ring/request request} (comp/get-query Session)) Session)]
    (index csrf-token (ssr/initial-state->script-tag initial-state))))

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
        legal-origins   (get config :legal-origins #{"localhost"})]
    (-> not-found-handler
      (wrap-api "/api")
      wrap-transit-params
      wrap-transit-response
      (wrap-html-routes)
      ;; If you want to set something like session store, you'd do it against
      ;; the defaults-config here (which comes from an EDN file, so it can't have
      ;; code initialized).
      ;; E.g. (wrap-defaults (assoc-in defaults-config [:session :store] (my-store)))
      (wrap-defaults defaults-config)
      wrap-gzip)))
