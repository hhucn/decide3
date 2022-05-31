(ns decide.server-components.pathom3
  (:require
   [com.fulcrologic.rad.pathom-common :as rpc]
   [com.fulcrologic.rad.pathom3 :as rad.pathom3]
   [com.wsscode.pathom.viz.ws-connector.core :as pvc]
   [com.wsscode.pathom.viz.ws-connector.pathom3 :as p.connector]
   [com.wsscode.pathom3.connect.built-in.plugins :as pbip]
   [com.wsscode.pathom3.connect.indexes :as pci]
   [com.wsscode.pathom3.connect.operation :as pco]
   [com.wsscode.pathom3.connect.planner :as pcp]
   [com.wsscode.pathom3.connect.runner :as-alias pcr]
   [com.wsscode.pathom3.error :as p.error]
   [com.wsscode.pathom3.interface.eql :as p.eql]
   [com.wsscode.pathom3.plugin :as p.plugin]
   [decide.models.argumentation.api :as argumentation.api]
   [decide.models.authorization :as auth]
   [decide.models.opinion.api :as opinion.api]
   [decide.models.process.resolver :as process.api]
   [decide.models.proposal.api :as proposal.api]
   [decide.models.user :as user]
   [decide.models.user.api :as user.api]
   [decide.server-components.access-checker :as access-checker]
   [decide.server-components.config :refer [config]]
   [decide.server-components.database :refer [conn]]
   [decide.server-components.eql-api.spec-plugin :refer [validate-specs]]
   [decide.ui.translations.load :as translation]
   [edn-query-language.core :as eql]
   [me.ebbinghaus.pathom-access-plugin.pathom3 :as access-plugin]
   [mount.core :as mount]))


(def all-resolvers
  [user/resolvers
   process.api/all-resolvers
   proposal.api/full-api
   opinion.api/resolvers

   translation/locale-resolver

   user.api/all-resolvers
   argumentation.api/full-api])


(defn database-plugin
  "Adds the connection and current database value to the environment."
  [conn]
  (assoc (pbip/env-wrap-plugin #(assoc % :conn conn, :db @conn))
    ::p.plugin/id `database-plugin))

(defn- pathom2-key? [key]
  (= "com.wsscode.pathom.connect" (namespace key)))

(defn- remove-pathom2-keys [config]
  (into {} (remove #(pathom2-key? (key %))) config))

(letfn [(p2-resolver? [r] (and (map? r) (contains? r :com.wsscode.pathom.connect/resolve)))
        (p2-mutation? [r] (and (map? r) (contains? r :com.wsscode.pathom.connect/mutate)))
        (p2? [r] (or (p2-resolver? r) (p2-mutation? r)))]
  (defn pathom2->pathom3
    "Converts a Pathom 2 resolver or mutation into one that will work with Pathom 3.

    Pathom 2 uses plain maps for these, and the following keys are recognized and supported:

    ::pc/sym -> ::pco/op-name
    ::pc/input -> ::pco/input as EQL
    ::pc/output -> ::pco/output
    ::pc/batch? -> ::pco/batch?
    ::pc/transform -> applied before conversion
    ::pc/mutate
    ::pc/resolve

    Returns the input unchanged of the given item is not a p2 artifact.

    NOTE: Any `transform` is applied at conversion time. Also, if your Pathom 2 resolver returns a value
    using Pathom 2 `final`, then that will not be converted into Pathom 3 by this function.

    You should manually convert that resolver by hand and use the new final support in Pathom 3.
     "
    [resolver-or-mutation]
    (if (p2? resolver-or-mutation)
      (let [{:com.wsscode.pathom.connect/keys [transform]} resolver-or-mutation
            {:com.wsscode.pathom.connect/keys [resolve batch? sym input output mutate] :as config} (cond-> resolver-or-mutation
                                                                                                     transform (transform))
            config (cond-> (remove-pathom2-keys config)
                     input (assoc ::pco/input (vec input))
                     batch? (assoc ::pco/batch? batch?)
                     output (assoc ::pco/output output))]
        (if resolve
          (pco/resolver sym config resolve)
          (pco/mutation sym config mutate)))
      resolver-or-mutation))

  (defn convert-resolvers
    "Convert a single or sequence (or nested sequences) of P2 resolvers (and/or mutations) to P3."
    [resolver-or-resolvers]
    (cond
      (sequential? resolver-or-resolvers) (mapv convert-resolvers resolver-or-resolvers)
      (p2? resolver-or-resolvers) (pathom2->pathom3 resolver-or-resolvers)
      :else resolver-or-resolvers)))


(defn- new-processor
  "Create a new EQL processor. You may pass Pathom 2 resolvers or mutations to this function, but beware
   that the translation is not 100% perfect, since the `env` is different between the two versions.

   The config options go under :com.fulcrologic.rad.pathom/config, and include:

   - `:log-requests? boolean` Enable logging of incoming queries/mutations.
   - `:log-responses? boolean` Enable logging of parser results.
   - `:sensitive-keys` a set of keywords that should not have their values logged
   "
  [{{:keys [log-requests? log-responses? trace?]} :com.fulcrologic.rad.pathom/config} static-env extra-plugins resolvers]
  (let [plan-cache* (atom {})
        base-env    (-> static-env
                      (p.plugin/register extra-plugins)
                      (p.plugin/register-plugin rad.pathom3/attribute-error-plugin)
                      (p.plugin/register-plugin rad.pathom3/rewrite-mutation-exceptions)
                      (pci/register (convert-resolvers resolvers))
                      (pcp/with-plan-cache plan-cache*))
        base-env    (cond-> base-env trace? (p.connector/connect-env {::pvc/parser-id ::env}))
        process     (p.eql/boundary-interface base-env)]
    (fn [env tx]
      (when (and log-requests? (not trace?))
        (rpc/log-request! {:env env, :tx tx}))
      (let [ast           (eql/query->ast tx)
            env-extension (merge env
                            {:parser p.eql/process
                             :query-params (rpc/combined-query-params ast)})
            response      (process env-extension tx)]
        (when (and log-responses? (not trace?))
          (rpc/log-response! base-env response))
        response))))

(defn make-processor [config conn]
  (new-processor
    config
    {:config config
     ::p.error/lenient-mode? true}
    [validate-specs
     (database-plugin conn)
     (pbip/env-wrap-plugin auth/session-wrapper)
     (access-plugin/access-plugin {:check-fn access-checker/check-access!})]
    all-resolvers))

(mount/defstate processor
  :start
  (make-processor config conn))
