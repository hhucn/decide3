(ns decide.server-components.pathom
  (:require
    [clojure.string :as str]
    [com.fulcrologic.rad.pathom :as rad-pathom]
    [com.wsscode.pathom.connect :as pc]
    [com.wsscode.pathom.core :as p]
    [datahike.api :as d]
    [decide.api.process :as api.process]
    [decide.models.argument :as argument]
    [decide.models.authorization :as auth]
    [decide.models.opinion :as opinion]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]
    [decide.server-components.config :refer [config]]
    [decide.server-components.database :refer [conn]]
    [mount.core :refer [defstate]]
    [taoensso.timbre :as log]
    [decide.models.authorization :as auth]
    [clojure.spec.alpha :as s]))

(defn- remove-keys-from-map-values [m & ks]
  (->> m
    (map (fn [[k v]] [k (apply dissoc v ks)]))
    (into {})))

(pc/defresolver index-explorer [env _]
  {::pc/input  #{:com.wsscode.pathom.viz.index-explorer/id}
   ::pc/output [:com.wsscode.pathom.viz.index-explorer/index]}
  {:com.wsscode.pathom.viz.index-explorer/index
   (-> (get env ::pc/indexes)
     (update ::pc/index-attributes (fn [attribute-map]
                                     (let [attribute-keys (keys attribute-map)
                                           keys-to-keep (remove (fn [k]
                                                                  (if (keyword? k)
                                                                    (some-> k namespace (str/starts-with? "com.wsscode.pathom.connect"))
                                                                    k))
                                                          attribute-keys)]
                                       (select-keys attribute-map keys-to-keep))))
     ; this is necessary for now, because the index contains functions which can not be serialized by transit.
     (update ::pc/index-resolvers remove-keys-from-map-values ::pc/resolve ::pc/transform)
     (update ::pc/index-mutations remove-keys-from-map-values ::pc/mutate ::pc/transform)
     ; to minimize clutter in the Index Explorer
     (update ::pc/index-resolvers (fn [rs] (apply dissoc rs (filter #(str/starts-with? (namespace %) "com.wsscode.pathom") (keys rs)))))
     (update ::pc/index-mutations (fn [rs] (apply dissoc rs (filter #(str/starts-with? (namespace %) "com.wsscode.pathom") (keys rs))))))})

(def spec-plugin
  {::p/wrap-mutate
   (fn [mutate]
     (fn [env sym params]
       (if-let [spec (get-in env [::pc/indexes ::pc/index-mutations sym ::s/params])]
         (if (s/valid? spec params)
           (mutate env sym params)
           (do
             (log/warn (s/explain spec params))
             (throw (ex-info "Failed validation!" (s/explain-data spec params)))))
         (mutate env sym params))))})

;;;
;;; This is borrowed from com.fulcrologic.rad.pathom
;;;

(defn parser-args [config plugins resolvers]
  (let [{:keys [trace? log-requests? log-responses? no-tempids?]} (::rad-pathom/config config)]
    {::p/mutate pc/mutate
     ::p/env {::p/reader [p/map-reader pc/reader2 pc/index-reader
                          pc/open-ident-reader p/env-placeholder-reader]
              ::p/placeholder-prefixes #{">"}
              ::pc/mutation-join-globals [(when-not no-tempids? :tempids) :errors]}
     ::p/plugins (into []
                   (keep identity
                     (concat
                       [(pc/connect-plugin {::pc/register resolvers})]
                       plugins
                       [(p/env-plugin {::p/process-error rad-pathom/process-error})
                        (when log-requests? (p/pre-process-parser-plugin rad-pathom/log-request!))
                        ;; TODO: Do we need this, and if so, we need to pass the attribute map
                        ;(p/post-process-parser-plugin add-empty-vectors)
                        spec-plugin
                        (p/post-process-parser-plugin p/elide-not-found)
                        (p/post-process-parser-plugin rad-pathom/elide-reader-errors)
                        (when log-responses? (rad-pathom/post-process-parser-plugin-with-env rad-pathom/log-response!))
                        rad-pathom/query-params-to-env-plugin
                        p/error-handler-plugin
                        (when trace? p/trace-plugin)])))}))

(defn new-parser
  "Create a new pathom parser. `config` is a map containing a ::config key with parameters
  that affect the parser. `extra-plugins` is a sequence of pathom plugins to add to the parser. The
  plugins will typically need to include plugins from any storage adapters that are being used,
  such as the `datomic/pathom-plugin`.
  `resolvers` is a vector of all of the resolvers to register with the parser, which can be a nested collection.

  Supported config options under the ::config key:

  - `:trace? true` Enable the return of pathom performance trace data (development only, high overhead)
  - `:log-requests? boolean` Enable logging of incoming queries/mutations.
  - `:log-responses? boolean` Enable logging of parser results."
  [config extra-plugins resolvers]
  (let [real-parser (p/parser (parser-args config extra-plugins resolvers))
        {:keys [trace?]} (get config ::rad-pathom/config {})]
    (fn wrapped-parser [env tx]
      (real-parser env (if trace?
                         (conj tx :com.wsscode.pathom/trace)
                         tx)))))

(def all-resolvers
  [index-explorer
   user/resolvers
   api.process/all-resolvers
   proposal/resolvers
   argument/resolvers
   opinion/resolvers])

(defn build-parser [config conn]
  (new-parser config
    [(p/env-plugin {:config config})
     (p/env-wrap-plugin auth/session-wrapper)
     (p/env-wrap-plugin
       (fn db-wrapper [env]
         (assoc env
           :conn conn
           :db (d/db conn))))]
    all-resolvers))

(defstate parser
  :start
  (new-parser config
    [(p/env-plugin {:config config})
     (p/env-wrap-plugin auth/session-wrapper)
     (p/env-wrap-plugin
       (fn db-wrapper [env]
         (merge
           {:conn conn
            :db (d/db conn)}
           env)))]
    all-resolvers))
