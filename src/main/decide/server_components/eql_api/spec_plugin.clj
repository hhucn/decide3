(ns decide.server-components.eql-api.spec-plugin
  (:require
   [clojure.spec.alpha :as s]
   [com.wsscode.pathom3.connect.indexes :as pci]
   [com.wsscode.pathom3.connect.runner :as-alias pcr]
   [com.wsscode.pathom3.plugin :as p.plugin]))


(defn wrap-with-spec-validation
  "Wraps the mutations with spec validation."
  [mutate]
  (fn [env ast]
    (if-let [spec (::s/params (pci/mutation-config env (:key ast)) (s/keys))]
      (let [params (:params ast)]
        (if (s/valid? spec params)
          (mutate env ast)
          (throw (ex-info "Failed validation!" (s/explain-data spec params)))))
      (mutate env ast))))


(def validate-specs
  {::p.plugin/id `validate-specs
   ::pcr/wrap-mutate wrap-with-spec-validation})
