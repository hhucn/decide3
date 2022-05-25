(ns me.ebbinghaus.pathom-access-plugin.pathom3
  (:require
   [com.wsscode.pathom3.connect.runner :as-alias pcr]
   [com.wsscode.pathom3.plugin :as-alias p.plugin]
   [me.ebbinghaus.pathom-access-plugin :as-alias plugin-ns]
   [me.ebbinghaus.pathom-access-plugin.shared :as shared]))


(defn access-plugin
  "Plugin to allow or deny resolving based on a specific input.
  Takes a map with following keys:

  `check-fn` (fn [env input]) -> boolean
  Takes the env and an `input` as a map entry [key value].
  Will be called for each resolver and each input key/value pair.

  IMPORTANT!
  You are responsible to call `allow!`, when you want to cache the result for the resolver.

  IMPORTANT!
  This doesn't work for nested inputs.

  `denied-fn` -> (fn [env input])
  Callback when a resolver is denied.

  `make-cache` (fn [env]) -> `AccessCache`
  Get called once everytime the parser is called. Needs to return `AccessCache`. Default is an empty `AtomCache`"
  [{:keys [make-cache check-fn denied-fn]
    :or {make-cache shared/default-make-cache-fn
         check-fn shared/default-check-fn
         denied-fn shared/default-denied-fn}
    :as config}]
  {::p.plugin/id `access-plugin


   ::pcr/wrap-root-run-graph!
   (fn track-request-root-run-external [process]
     (fn track-request-root-run-internal [env ast entity*]
       (process
         (assoc env ::plugin-ns/access-cache (make-cache env))
         ast
         entity*)))

   ::pcr/wrap-resolve
   (shared/resolver-wrapper config)})
