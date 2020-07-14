(ns decide.routing
  (:require
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.guardrails.core :refer [>defn =>]]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [taoensso.timbre :as log]
    [clojure.string :refer [split]]
    [cljs.spec.alpha :as s]
    [clojure.string :as str]
    [edn-query-language.core :as eql]
    [com.fulcrologic.fulcro.components :as comp]
    [pushy.core :as pushy]))

; code from https://chrisodonnell.dev/posts/giftlist/routing/
; Thanks a lot!

(def default-route ["login"])
(def history (atom nil))
(s/def ::path (s/coll-of string?))

(>defn url->path
  "Given a url of the form \"/gift/123/edit?code=abcdef\", returns a
  path vector of the form [\"gift\" \"123\" \"edit\"]. Assumes the url
  starts with a forward slash. An empty url yields the path [\"home\"]
  instead of []."
  [url]
  [string? => ::path]
  (-> url (str/split "?") first (str/split "/") rest vec))

(>defn path->url
  "Given a path vector of the form [\"gift\" \"123\" \"edit\"],
  returns a url of the form \"/gift/123/edit\"."
  [path]
  [::path => string?]
  (str/join (interleave (repeat "/") path)))

(defn path-to->url [& targets-and-params]
  (path->url (apply dr/path-to targets-and-params)))

(>defn routable-path?
  "True if there exists a router target for the given path."
  [app path]
  [app/fulcro-app? ::path => boolean?]
  (let [state-map (app/current-state app)
        root-class (app/root-class app)
        root-query (comp/get-query root-class state-map)
        ast (eql/query->ast root-query)]
    (some? (dr/ast-node-for-route ast path))))

(>defn route-to! [path]
  [(s/coll-of string?) => nil?]
  (pushy/set-token! @history (path->url path)))

(defn start-history! [app]
  (reset! history
    (pushy/pushy
      (fn [path]
        (if (routable-path? app path)
          (dr/change-route! app path)
          ;; change URL and dispatch again
          (route-to! default-route)))                       ; 404 ?
      url->path)))

(defn start! []
  (pushy/start! @history))


(defmutation route-to
  "Mutation to go to a specific route"
  [{:keys [path]}]
  (action [_]
    (route-to! path)))

(defn with-route [this path props]
  (merge
    {:href (path->url path)}
    props))