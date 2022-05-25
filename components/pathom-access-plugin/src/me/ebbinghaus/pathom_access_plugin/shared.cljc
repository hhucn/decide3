(ns me.ebbinghaus.pathom-access-plugin.shared
  (:require
   [me.ebbinghaus.pathom-access-plugin.api :as api]))

(defn default-make-cache-fn [_env]
  (api/make-atom-cache))

(defn default-check-fn [_env _input]
  false)

(defn default-denied-fn [_env _input]
  nil)

(defn resolver-wrapper [{:keys [check-fn denied-fn]
                         :or {check-fn default-check-fn
                              denied-fn default-denied-fn}}]
  (letfn [(has-access? [env input]
            (or
              (api/allowed? env input)
              (check-fn env input)))]
    (fn wrap-resolver-creation [resolve]
      (fn wrap-resolver-call [env input]
        (let [every-input-allowed?
              (fn every-input-allowed? [input]
                (every? #(has-access? env %) input))
              allowed?
              (if (seq? input)                            ; batched?
                (every? every-input-allowed? input)
                (every-input-allowed? input))]
          (if allowed?
            (resolve env input)
            (denied-fn env input)))))))