(ns decide.server-components.eql-api.transformer
  (:require
   [com.wsscode.pathom.connect :as-alias pc]))

(defn needs-login [{::pc/keys [mutate] :as mutation}]
  (assoc mutation
    ::pc/mutate
    (fn [env params]
      (if (some? (:AUTH/user env))
        (mutate env params)
        {:error "Needs login"}))))