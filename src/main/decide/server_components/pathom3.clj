(ns decide.server-components.pathom3
  (:require
   [com.fulcrologic.rad.pathom-common :as rpc]
   [com.fulcrologic.rad.pathom3 :as rad.pathom3]
   [com.wsscode.pathom.viz.ws-connector.core :as pvc]
   [com.wsscode.pathom.viz.ws-connector.pathom3 :as p.connector]
   [com.wsscode.pathom3.connect.built-in.plugins :as pbip]
   [com.wsscode.pathom3.connect.indexes :as pci]
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
                      (pci/register (rad.pathom3/convert-resolvers resolvers))
                      (pcp/with-plan-cache plan-cache*))
        base-env    (cond-> base-env trace? (p.connector/connect-env {::pvc/parser-id ::env}))
        process     (p.eql/boundary-interface base-env)]
    (fn [env tx]
      (when (and log-requests? (not trace?))
        (rpc/log-request! {:env env, :tx tx}))
      (let [ast           (eql/query->ast tx)
            env-extension #(merge %
                             env
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
    [(database-plugin conn)
     (pbip/env-wrap-plugin auth/session-wrapper)
     #_(access-plugin/access-plugin {:check-fn access-checker/check-access!})]
    all-resolvers))

(mount/defstate processor
  :start
  (make-processor config conn))
