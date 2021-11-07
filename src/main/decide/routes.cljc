(ns decide.routes
  (:require
    [#?(:clj  clojure.spec.alpha
        :cljs cljs.spec.alpha) :as s]
    [#?(:clj  clojure.spec.gen.alpha
        :cljs cljs.spec.gen.alpha) :as gen]
    [clojure.string :as str]
    [com.fulcrologic.guardrails.core :refer [>def >defn- >defn => | ?]]
    [reitit.spec :as rs]
    #?(:cljs [reitit.frontend.easy :as rfe]))
  #?(:cljs (:require-macros [decide.routes])))

(def top-routes
  [["/" {:name ::landing, :segment ["decisions"]}]
   ["/decisions" ::process-list]
   ["/decision/{process/slug}"
    {:name ::process-context
     :parameters {:path {:process/slug string?}}}

    ["/" {:name ::process-default, :segment ["proposals"]}]
    ["/home" {:name ::process-home}]
    ["/proposals" {:name ::process-all-proposals}]
    ["/proposal/{proposal/id}"
     {:parameters {:path {:proposal/id uuid?}}
      :name ::proposal-detail-page}]
    ["/dashboard" ::process-dashboard]
    ["/moderate" ::process-moderation]]

   ["/settings" ::settings]
   ["/help" ::help]
   ["/privacy" ::privacy]

   ["/api"]])


(>def ::path ::rs/path)
(>def ::string-segment (s/and string? #(not (str/includes? % "/"))))
(>def ::segment
  (s/coll-of (s/or :string ::string-segment, :parameter keyword?)
    :kind vector? :min-count 1))
(>def ::filled-segment
  (s/coll-of (s/or :string ::string-segment, :value (s/and any? (complement sequential?)))
    :kind vector? :min-count 1))
(>def ::raw-routes ::rs/raw-routes)
(>def ::arg (s/or :name keyword? :data ::rs/data))
(>def ::raw-route (s/with-gen (s/and sequential? ::rs/raw-route)
                    #(gen/cat (s/gen (s/cat :path ::path :arg (s/? ::arg))))))

(>defn path->segment
  "Returns a segment for a given path."
  [path]
  [::path | #(seq (rest (str/split path #"/(?![^{]*})"))) => ::segment]
  (let [string-segment (rest (str/split path #"/(?![^{]*})"))] ; Match / but not if it is inside a curly braces.
    (mapv
      (fn [s]
        (if (#{\{ \:} (first s))
          (keyword (str/replace s #"[:{}]" ""))
          s))
      string-segment)))

(>defn flatten-children [children]
  [(s/coll-of ::raw-route) => (s/coll-of ::raw-route)]
  (mapcat
    (fn [x]
      (if (string? (first x))
        [x]
        (flatten-children x)))
    children))

(defn assoc-segment [[path data & children]]
  [::raw-route => ::raw-route]
  (if (nil? data)
    [path]
    (let [new-data (let [segment (or (:segment data) (path->segment path))
                         segment-map {:segment-route segment
                                      :segment segment}
                         name (or (:name data) data)]
                     (-> segment-map
                       (merge (if (keyword? data) {:name data} data))
                       (update :segment with-meta {:displace true
                                                   ::name name})))]
      (if (nil? children)
        [path new-data]
        [path new-data (mapv assoc-segment (flatten-children children))]))))

(def all-routes (mapv assoc-segment top-routes))

(def -de-nest
  "Transducer to flatten the route tree."
  (mapcat
    (fn [[route data & children]]
      (let [children (if (string? (ffirst children)) children (first children))]
        (when route
          (into [[route data]] -de-nest children))))))

(>defn segmentize [[path data]]
  [::raw-route => (? (s/tuple keyword? ::segment))]
  (when-let [name (if (keyword? data) data (:name data))]
    (let [segment (or
                    (when-not (keyword? data) (:segment data))
                    (path->segment path))]
      (when-not (empty? segment) [name segment]))))

(def segment-index
  (into {} (comp -de-nest (map segmentize)) all-routes))

#?(:clj
   (defmacro segment [name]
     (if (contains? segment-index name)
       (with-meta (segment-index name) {::name name})
       (throw (ex-info "Name unknown!" {:name name})))))

(>defn path->href
  "Given a path vector of the form [\"gift\" \"123\" \"edit\"],
  returns an absolute path of the form \"/gift/123/edit\"."
  [path]
  [::filled-segment => ::path]
  (str "/" (str/join "/" path)))

#?(:cljs (def href rfe/href))
