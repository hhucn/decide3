(ns decide.models.opinion.api
  (:require
    [com.fulcrologic.fulcro.algorithms.normalized-state :as norm-state]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.raw.components :as raw.comp]
    [decide.models.opinion :as opinion]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]))

(defn remove-opinion-of-user [proposal user]
  (update proposal ::proposal/opinions
    #(vec
       (remove
         (fn [opinion]
           (= (::user/id user) (-> opinion ::opinion/user second)))
         %))))

(defn set* [{::proposal/keys [my-opinion pro-votes] :as proposal} new-opinion]
  (-> proposal
    (assoc
      ::proposal/my-opinion new-opinion
      ::proposal/pro-votes (+ pro-votes (- new-opinion my-opinion)))
    #_(update ::proposal/opinions add-opinion {::opinion/value new-opinion
                                               ::opinion/user [::user/id user-id]})))

(defn set-opinion [proposal opinion]
  (let [old-value (::proposal/my-opinion proposal)
        new-value (::opinion/value opinion)]
    (-> proposal
      (assoc ::proposal/my-opinion (::opinion/value opinion))
      (update ::proposal/pro-votes #(-> % (- (max 0 old-value)) (+ (max 0 new-value))))
      (update ::proposal/con-votes #(-> % (- (min 0 old-value)) (+ (min 0 new-value))))

      ;; remove and add opinion, so that there no duplicates
      (remove-opinion-of-user (::opinion/user opinion))
      (update ::proposal/opinions conj (update opinion ::opinion/user #(find % ::user/id))))))  ; manual ident.. :-/

(defn- neutralize-proposal [{::proposal/keys [my-opinion] :as proposal} user-id]
  (set-opinion proposal {::opinion/value 0
                         ::opinion/user {::user/id user-id}}))

(defn neutralize-all-proposals [state process-ref {::user/keys [id]}]
  (let [proposal-idents (get-in state (conj process-ref ::process/proposals))]
    (reduce
      (fn [state proposal-ident]
        (update-in state proposal-ident neutralize-proposal id))
      state proposal-idents)))

{::proposal/id {42 {::proposal/id 42
                    ::proposal/my-opinion 1
                    ::proposal/pro-votes 5
                    ::proposal/opinions [{::opinion/value 1
                                          ::opinion/user [::user/id 1337]}]}}}


(defmutation add [{::proposal/keys [id]
                   :keys [opinion]}]
  (action [{:keys [state]}]
    (swap! state
      (fn [state]
        (let [current-user (norm-state/get-in-graph state [:root/current-session :user])
              current-process-ident (get state :ui/current-process) ; get this as a parameter? Maybe even the ref?
              current-process (get-in state current-process-ident)]
          (cond-> state
            (process/single-approve? current-process) (neutralize-all-proposals current-process-ident current-user)
            :always (update-in [::proposal/id id] set-opinion {::opinion/value opinion
                                                               ::opinion/user current-user}))))))
  (remote [env]
    (-> env
      (m/returning
        (raw.comp/nc
          [::proposal/id
           ::proposal/my-opinion
           ::proposal/pro-votes
           {::proposal/opinions
            [::opinion/value
             {::opinion/user
              [::user/id]}]}])))))