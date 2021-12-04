(ns me.ebbinghaus.pathom2-access-plugin.core)

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
  (some-> env ::access-cache (put! inputs)))

(defn allow!
  "Allows `inputs` for future resolver calls in this query."
  [env & inputs]
  (allow-many! env inputs))

(defn allowed?
  "Checks if current input has already been allowed."
  [env input]
  (some-> env ::access-cache (hit? input)))

(defn- default-make-cache-fn [_env]
  (make-atom-cache))

(defn- default-check-fn [_env _input]
  false)

(defn- default-denied-fn [_env _input]
  nil)

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
     :or {make-cache default-make-cache-fn
          check-fn default-check-fn
          denied-fn default-denied-fn}}]
   {:com.wsscode.pathom.core/wrap-parser
    (fn wrap-parser-creation [parser]
      (fn wrap-parser-call [env tx]
        (-> env
          (assoc ::access-cache (make-cache env))
          (parser tx))))

    :com.wsscode.pathom.connect/wrap-resolve
    (letfn [(has-access? [env input]
              (or
                (allowed? env input)
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
              (denied-fn env input))))))}))
