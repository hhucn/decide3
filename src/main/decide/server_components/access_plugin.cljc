(ns decide.server-components.access-plugin
  (:require
    [com.wsscode.pathom.connect :as pc]
    [com.wsscode.pathom.core :as p]))

(defn allow-many! [env inputs]
  (swap! (::access-cache env) #(apply conj % inputs))
  env)

(defn allow! [env & inputs]
  (allow-many! env inputs))

(defn allowed? [env input]
  (contains? @(::access-cache env) input))

(defn- initial-cache [_env]
  (atom #{}))

(defn access-plugin
  ([check-fn]
   (access-plugin check-fn {}))
  ([check-fn {:keys [initial-cache-fn]
              :or {initial-cache-fn initial-cache}}]
   {::p/wrap-parser
    (fn [parser]
      (fn [env tx]
        (-> env
          (assoc ::access-cache (initial-cache-fn env))
          (parser tx))))

    ::pc/wrap-resolve
    (letfn [(has-access? [env input]
              (or
                (allowed? env input)
                (check-fn env input)))]
      (fn [resolve]
        (fn [env input]
          (let [allowed?
                (if (seq? input)                               ; batched?
                  (every? (fn [input] (every? #(has-access? env %) input)) input)
                  (every? #(has-access? env %) input))]
            (when allowed?
              (resolve env input))))))}))
