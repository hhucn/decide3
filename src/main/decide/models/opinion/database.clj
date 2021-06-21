(ns decide.models.opinion.database
  (:require
    [clojure.spec.alpha :as s]
    [com.fulcrologic.guardrails.core :refer [>defn =>]]
    [datahike.api :as d]
    [datahike.core :as d.core]
    [decide.models.opinion :as opinion]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]))

(def approves-rule
  '[[(approves? ?user ?proposal)
     [?user ::user/opinions ?opinion]
     [?proposal ::proposal/opinions ?opinion]
     [?opinion ::opinion/value +1]]
    [(undecided? ?user ?proposal)
     (or-join [?user ?proposal]
       (not
         [?user ::user/opinions ?opinion]
         [?proposal ::proposal/opinions ?opinion])
       (and
         [?user ::user/opinions ?opinion]
         [?proposal ::proposal/opinions ?opinion]
         [?opinion ::opinion/value 0]))]])

(>defn get-opinion [db user-ident proposal-ident]
  [d.core/db? ::user/lookup ::proposal/ident => (s/nilable nat-int?)]
  (d/q '[:find ?opinion .
         :in $ ?user ?proposal
         :where
         [?user ::user/opinions ?opinion]
         [?proposal ::proposal/opinions ?opinion]]
    db
    user-ident
    proposal-ident))

(>defn set-opinion! [conn user-ident proposal-ident opinion-value]
  [d.core/conn? ::user/lookup ::proposal/lookup ::opinion/value => map?]
  (if-let [opinion-id (get-opinion @conn user-ident proposal-ident)]
    (d/transact conn
      [{:db/id  opinion-id
        ::opinion/value opinion-value}])
    (d/transact conn
      [[:db/add proposal-ident ::proposal/opinions "temp"]
       [:db/add user-ident ::user/opinions "temp"]
       {:db/id  "temp"
        ::opinion/value opinion-value}])))

(defn get-values-for-proposal [db proposal-ident]
  (merge
    {1 0, -1 0}                                             ; default values
    (->> proposal-ident
      (d/pull db [{::proposal/opinions [::opinion/value]}])
      ::proposal/opinions
      (map :decide.models.opinion/value)
      frequencies)))