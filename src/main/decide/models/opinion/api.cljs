(ns decide.models.opinion.api
  (:refer-clojure :exclude [set])
  (:require
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.raw.components :as raw.comp]
    [decide.models.opinion :as opinion]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]))

(defn user's-opinion? [opinion user]
  (= (::user/id user) (-> opinion ::opinion/user second)))  ; `second` as the opinion is normalized

(defn remove-opinion-of-user [opinions user]
  (vec (remove #(user's-opinion? % user) opinions)))

(defn conj-opinion-to-opinions [proposal opinion]
  (let [opinion (update opinion ::opinion/user #(find % ::user/id))] ; manual ident.. :-/
    (-> proposal
      ;; remove and add opinion, so that there no duplicates
      (update ::proposal/opinions remove-opinion-of-user (::opinion/user opinion))
      (update ::proposal/opinions conj opinion))))

(defn- votes-field-updater
  "Returns a function to update a vote fields. "
  [type-pred old new]
  (if (= (type-pred old) (type-pred new))
    #(or % 0)                                               ; The type didn't change.
    (if (type-pred new)                                     ; Did it change from or to type?
      inc
      dec)))

(defn update-vote-fields [proposal old-value new-value]
  (-> proposal
    (update ::proposal/pro-votes (votes-field-updater opinion/approval-value? old-value new-value))
    (update ::proposal/con-votes (votes-field-updater opinion/reject-value? old-value new-value))
    (update ::proposal/favorite-votes (votes-field-updater opinion/favorite-value? old-value new-value))))


;; TODO Add test for this
(defn set-opinion [proposal opinion]
  (let [old-value (::proposal/my-opinion-value proposal)
        new-value (::opinion/value opinion)]
    (if (= old-value new-value)
      proposal
      (-> proposal
        (assoc
          ::proposal/my-opinion opinion
          ::proposal/my-opinion-value (::opinion/value opinion))
        (update-vote-fields old-value new-value)
        (conj-opinion-to-opinions opinion)))))

(defn update-proposals [state process-ref f & args]
  (let [proposal-idents (get-in state (conj process-ref ::process/proposals))]
    (reduce
      (fn [state proposal-ident]
        (apply update-in state proposal-ident f args))
      state proposal-idents)))


(defn neutralize-all-proposals [state process-ref user]
  (update-proposals state process-ref
    set-opinion
    {::opinion/value 0
     ::opinion/user user}))

{::proposal/id {42 {::proposal/id 42
                    ::proposal/my-opinion-value 1
                    ::proposal/pro-votes 5
                    ::proposal/opinions [{::opinion/value 1
                                          ::opinion/user [::user/id 1337]}]}}}

(defn de-favorite-all [state process-ref user]
  (update-proposals state process-ref
    (fn [proposal]
      (cond-> proposal
        (opinion/favorite? (::proposal/my-opinion proposal))
        (set-opinion {::opinion/value 1
                      ::opinion/user user})))))

(defmutation add [{::proposal/keys [id]
                   opinion-value :opinion}]                 ; TODO refactor this
  (action [{:keys [state]}]

    (swap! state
      (fn [state]
        (let [current-user (user/current state)
              current-process (process/current state)
              process-ref (find current-process ::process/slug)
              new-opinion {::opinion/value opinion-value
                           ::opinion/user current-user}]
          (cond-> state
            (process/single-approve? current-process)
            (neutralize-all-proposals process-ref current-user)

            (opinion/favorite? new-opinion)
            (de-favorite-all process-ref current-user)

            :always
            (update-in [::proposal/id id] set-opinion new-opinion))))))
  (remote [env]
    (-> env
      (m/returning
        (raw.comp/nc
          [::proposal/id
           ::proposal/my-opinion-value
           {::proposal/my-opinion [::opinion/value]}
           ::proposal/pro-votes
           {::proposal/opinions
            [::opinion/value
             {::opinion/user
              [::user/id]}]}])))))