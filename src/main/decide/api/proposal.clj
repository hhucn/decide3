(ns decide.api.proposal
  (:require
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [taoensso.timbre :as log]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]
    [datahike.api :as d]
    [datahike.core :as d.core])
  (:import (java.util Date)))


(defmutation add-proposal [{:keys [conn AUTH/user-id] :as env} {:proposal/keys [id title body parents]}]
  {::pc/params [:todo]
   ::pc/output [:todo/id]}
  (if user-id
    (let [real-id (str (rand-int 1000))
          proposal #::proposal{:id              real-id
                               :title           title
                               :body            body
                               :parents         (for [parent parents
                                                      :let [id (:proposal/id parent)]]
                                                  [::proposal/id id])
                               :original-author [::user/id user-id]
                               :created         (Date.)}
          tx-report (d/transact conn [proposal])]
      {:tempids {id real-id}
       ::p/env  (assoc env :db (:db-after tx-report))
       :proposal/id real-id})
    (throw (ex-info "User is not logged in!" {}))))

(defn get-todo [db id]
  (d/pull db [::id ::task ::done?] [::id id]))

(defresolver proposal-resolver [{:keys [db]} {:keys [proposal/id]}]
  {::pc/input  #{:proposal/id}
   ::pc/output [:proposal/id :proposal/title :proposal/body :proposal/created {:proposal/parents [:proposal/id]}]}
  (let [{::proposal/keys [id title body created parents]}
        (d/pull db [::proposal/id ::proposal/title ::proposal/body ::proposal/created
                    {::proposal/parents [::proposal/id]}]
          [::proposal/id id])]
    #:proposal{:id id :title title :body body :created created :parents (or parents [])}))

(defresolver all-proposal-ids [{:keys [db ]} _]
  {::pc/input  #{}
   ::pc/output [{:all-proposals [:proposal/id]}]}
  {:all-proposals
   (for [id (d/q '[:find [?id ...] :where [?e ::proposal/id ?id]] db)]
     {:proposal/id id})})



(def resolvers [add-proposal proposal-resolver all-proposal-ids
                ;; This doesn't have to be... :-/
                (pc/alias-resolver2 ::proposal/id :proposal/id)
                (pc/alias-resolver2 ::proposal/title :proposal/title)
                (pc/alias-resolver2 ::proposal/body :proposal/body)
                (pc/alias-resolver2 ::proposal/created :proposal/created)
                (pc/alias-resolver2 ::proposal/parents :proposal/parents)])