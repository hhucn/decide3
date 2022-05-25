(ns me.ebbinghaus.pathom-access-plugin.api
  (:require [me.ebbinghaus.pathom-access-plugin :as-alias plugin-ns]))

(defprotocol AccessCache
  (put! [this things] "Add `things` to cache.")
  (hit? [this thing] "Checks if `thing` is cached."))

(deftype AtomCache [cache]
  AccessCache
  (put! [_ xs] (swap! cache #(apply conj % xs)))
  (hit? [_ x] (contains? @cache x)))

(defn make-atom-cache
  ([] (make-atom-cache #{}))
  ([initial-state] (AtomCache. (atom initial-state))))

(defn allow-many!
  "Allows `inputs` for future resolver calls in this query."
  [env inputs]
  (some-> env ::plugin-ns/access-cache (put! inputs)))

(defn allow!
  "Allows `inputs` for future resolver calls in this query."
  [env & inputs]
  (allow-many! env inputs))

(defn allowed?
  "Checks if current input has already been allowed."
  [env input]
  (some-> env ::plugin-ns/access-cache (hit? input)))