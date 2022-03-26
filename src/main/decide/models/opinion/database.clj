(ns decide.models.opinion.database
  (:require
    [clojure.spec.alpha :as s]
    [com.fulcrologic.guardrails.core :refer [>defn >defn- => ?]]
    [datahike.api :as d]
    [datahike.core :as d.core]
    [decide.models.opinion :as opinion.legacy]
    [decide.models.process :as process]
    [decide.models.proposal :as-alias proposal]
    [decide.models.user :as user]
    [decide.opinion :as opinion]))

(defn =by [f & xs]
  (apply = (map f xs)))

(def rules
  '[[(approves? ?user ?proposal)
     [?user ::user/opinions ?opinion]
     [?proposal ::proposal/opinions ?opinion]
     [?opinion ::opinion.legacy/value +1]]
    [(undecided? ?user ?proposal)
     (or-join [?user ?proposal]
       (not
         [?user ::user/opinions ?opinion]
         [?proposal ::proposal/opinions ?opinion])
       (and
         [?user ::user/opinions ?opinion]
         [?proposal ::proposal/opinions ?opinion]
         [?opinion ::opinion.legacy/value 0]))]

    [(users-process-opinions ?user ?process ?opinion)
     [?user ::user/opinions ?opinion]
     [?proposal ::proposal/opinions ?opinion]
     [?process ::process/proposals ?proposal]]])


(>defn get-opinion [db user proposal]
  [d.core/db? ::user/entity ::proposal/entity => (s/nilable ::opinion.legacy/entity)]
  (when-not (string? (:db/id proposal))                     ; catch tempid
    (when-let [opinion-id (d/q '[:find ?opinion .
                                 :in $ ?user ?proposal
                                 :where
                                 [?user ::user/opinions ?opinion]
                                 [?proposal ::proposal/opinions ?opinion]]
                            db (:db/id user) (:db/id proposal))]
      (d/entity db opinion-id))))

(def opinion-pattern
  [:db/id
   ::opinion.legacy/value
   {::proposal/_opinions [:db/id]}
   {::user/_opinions [:db/id]}])

(defn- enrich [opinion]
  (merge opinion
    {::opinion.legacy/user (::user/_opinions opinion)
     ::opinion.legacy/proposal (::proposal/_opinions opinion)}))


(defn get-by-user+proposal [db user proposal]
  (when-let [opinion
             (d/q '[:find (pull ?opinion pattern) .
                    :in $ pattern ?user ?proposal
                    :where
                    [?user ::user/opinions ?opinion]
                    [?proposal ::proposal/opinions ?opinion]]
               db opinion-pattern
               (:db/id user)
               (:db/id proposal))]
    (enrich opinion)))

(defn get-by-user+process [db user process]
  (map enrich
    (d/q '[:find [(pull ?opinion pattern) ...]
           :in $ % pattern ?process ?user
           :where
           (users-process-opinions ?user ?process ?opinion)]
      db rules opinion-pattern (:db/id process) (:db/id user))))

(defn ->remove
  [opinion]
  [[:db.fn/retractEntity (:db/id opinion)]])

(defn- tempid [opinion]
  (str
    "new-opinion-" (get-in opinion [::opinion.legacy/user :db/id])
    "-" (get-in opinion [::opinion.legacy/proposal :db/id])))

(defn ->add [opinion]
  [(s/keys :req [::opinion.legacy/proposal ::opinion.legacy/user ::opinion.legacy/value]) => vector?]
  (let [{::opinion.legacy/keys [user proposal value]} opinion
        id (:db/id opinion (tempid opinion))]
    [{:db/id id ::opinion.legacy/value value}
     [:db/add (:db/id proposal) ::proposal/opinions id]
     [:db/add (:db/id user) ::user/opinions id]]))

(defn ->update [old-opinion new-opinion]
  (let [old-value (::opinion.legacy/value old-opinion)
        new-value (::opinion.legacy/value new-opinion)]
    (if (= new-value old-value)
      []                                                    ; do nothing
      [[:db/add (:db/id new-opinion) ::opinion.legacy/value new-value]])))

(defn ->un-favorite [opinion]
  (if (opinion.legacy/favorite? opinion)
    (->update opinion (assoc opinion ::opinion.legacy/value 1))
    []))

(defn ->set [db opinion]
  [d.core/db? (s/keys :req [::opinion.legacy/value
                            ::opinion.legacy/user
                            ::opinion.legacy/proposal])
   => vector?]
  (let [{::opinion.legacy/keys [user proposal]} opinion
        process (::proposal/process proposal)
        existing-opinion (::proposal/my-opinion proposal)

        all-opinions-of-user (get-by-user+process db user process)
        other-opinions-of-user (remove #(=by :db/id existing-opinion %) all-opinions-of-user)]

    (cond->
      (if existing-opinion
        (->update existing-opinion opinion)
        (->add opinion))

      (opinion.legacy/favorite? opinion)
      (into (mapcat ->un-favorite) other-opinions-of-user)

      (process/single-approve? process)
      (into (mapcat ->remove) other-opinions-of-user))))


(defn get-values-for-proposal [db proposal-ident]
  (merge
    {1 0, -1 0}                                             ; default values
    (->> proposal-ident
      (d/pull db [{::proposal/opinions [::opinion.legacy/value]}])
      ::proposal/opinions
      (map :decide.models.opinion/value)
      frequencies)))