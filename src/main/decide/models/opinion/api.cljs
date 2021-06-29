(ns decide.models.opinion.api
  (:require
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]))

(defn- neutralize-proposal [{::proposal/keys [my-opinion] :as proposal}]
  (cond-> proposal
    :always (dissoc ::proposal/my-opinion)
    ;; This may be overengineered. :-)
    (pos? my-opinion) (update ::proposal/pro-votes #(- % my-opinion))
    (neg? my-opinion) (update ::proposal/con-votes #(+ % my-opinion))))

(defn set-neutral [state process-ref]
  (let [proposal-idents (get-in state (conj process-ref ::process/proposals))]
    (reduce
      (fn [state proposal-ident]
        (update-in state proposal-ident neutralize-proposal))
      state proposal-idents)))

(defn set* [{::proposal/keys [my-opinion pro-votes] :as proposal} new-opinion]
  (assoc proposal
    ::proposal/my-opinion new-opinion
    ::proposal/pro-votes (+ pro-votes (- new-opinion my-opinion))))

(defmutation add [{::proposal/keys [id]
                   :keys [opinion]}]
  (action [{:keys [state]}]
    (let [current-process-ident (get @state :ui/current-process) ; get this as a parameter? Maybe even the ref?
          current-process (get-in @state current-process-ident)]
      (swap! state
        (fn [state]
          (cond-> state
            (process/single-approve? current-process) (set-neutral current-process-ident)
            :always (update-in [::proposal/id id] set* opinion))))))
  (remote [_] true))