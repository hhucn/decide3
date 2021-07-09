(ns decide.server-components.access-plugin
  (:require
    [com.wsscode.pathom.connect :as pc]
    [com.wsscode.pathom.core :as p]))

(defn allow-many! [env inputs]
  (some-> env
    ::access-cache
    (swap! #(apply conj % inputs)))
  env)

(defn allow! [env & inputs]
  (allow-many! env inputs))

(defn allowed? [env input]
  (some-> env
    ::access-cache
    deref
    (contains? input)))

(defn- initial-cache [_env]
  (atom #{}))

(defn access-plugin
  ([check-fn]
   (access-plugin check-fn {}))
  ([check-fn {:keys [initial-cache-fn]
              :or {initial-cache-fn initial-cache}}]
   {::p/wrap-parser
    (fn wrap-parser-creation [parser]
      (fn wrap-parser-call [env tx]
        (-> env
          (assoc ::access-cache (initial-cache-fn env))
          (parser tx))))

    ::pc/wrap-resolve
    (letfn [(has-access? [env input]
              (or
                (allowed? env input)
                (check-fn env input)))]
      (fn wrap-resolver-creation [resolve]
        (fn wrap-resolver-call [env input]
          (let [allowed?
                (if (seq? input)                            ; batched?
                  (every? (fn [input] (every? #(has-access? env %) input)) input)
                  (every? #(has-access? env %) input))]
            (when allowed?
              (resolve env input))))))}))
