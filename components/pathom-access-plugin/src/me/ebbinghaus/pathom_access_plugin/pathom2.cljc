(ns me.ebbinghaus.pathom-access-plugin.pathom2
  (:require
   [com.wsscode.pathom.connect :as-alias pc]
   [com.wsscode.pathom.core :as-alias p]
   [me.ebbinghaus.pathom-access-plugin :as-alias plugin-ns]
   [me.ebbinghaus.pathom-access-plugin.shared :as shared]))

(defn access-plugin
  "Plugin to allow or deny resolving based on a specific input.
  Takes a map with following keys:

  `check-fn` (fn [env input]) -> boolean
  Takes the env and an `input` as a map entry [key value].
  Will be called for each resolver and each input key/value pair.

  IMPORTANT!
  You are responsible to call `allow!`, when you want to cache the result for this query.

  `denied-fn` -> (fn [env input])
  Callback when a resolver is denied.

  `make-cache` (fn [env]) -> `AccessCache`
  Get called once everytime the parser is called. Needs to return `AccessCache`. Default is an empty `AtomCache`"
  ([] (access-plugin {}))
  ([{:keys [make-cache check-fn denied-fn]
     :or {make-cache shared/default-make-cache-fn
          check-fn shared/default-check-fn
          denied-fn shared/default-denied-fn}
     :as config}]
   {::p/wrap-parser
    (fn wrap-parser-creation [parser]
      (fn wrap-parser-call [env tx]
        (-> env
          (assoc ::plugin-ns/access-cache (make-cache env))
          (parser tx))))

    ::pc/wrap-resolve
    (shared/resolver-wrapper config)}))