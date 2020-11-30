(ns decide.server-components.pathom
  (:require
    [clojure.string :as str]
    [com.fulcrologic.rad.pathom :as rad-pathom]
    [mount.core :refer [defstate]]
    [com.wsscode.pathom.connect :as pc]
    [com.wsscode.pathom.core :as p]
    [com.wsscode.common.async-clj :refer [let-chan]]
    [decide.models.user :as user]
    [decide.models.proposal :as proposal.api]
    [decide.models.profile :as profile]
    [decide.models.statement :as statement]
    [decide.server-components.config :refer [config]]
    [decide.server-components.database :refer [conn]]
    [datahike.api :as d]))

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

(defstate parser
  :start
  (rad-pathom/new-parser config
    [(p/env-plugin {:conn conn :db (d/db conn)})
     (p/env-plugin {:config config})
     (p/env-wrap-plugin
       (fn [env]
         (let [session (get-in env [:ring/request :session])
               {nickname :profile/nickname
                valid?   :session/valid?} session]
           (merge
             {:AUTH/profile-nickname (when valid? nickname)}
             env))))]
    [index-explorer
     user/resolvers
     proposal.api/resolvers
     profile/resolvers
     statement/resolvers]))