(ns decide.api.proposal
  (:require
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [taoensso.timbre :as log]
    [datahike.api :as d]
    [datahike.core :as d.core])
  (:import (java.util Date)))


(defmutation add-proposal [{:keys [conn AUTH/user-id] :as env} {:proposal/keys [id title body parents]}]
  {::pc/output [:proposal/id]}
  (if user-id
    (let [real-id (str (rand-int 1000))
          proposal #:proposal{:id              real-id
                               :title           title
                               :body            body
                               :parents         (for [parent parents
                                                      :let [id (:proposal/id parent)]]
                                                  [:proposal/id id])
                               :original-author [:user/id user-id]
                               :created         (Date.)}
          tx-report (d/transact conn [proposal])]
      {:tempids     {id real-id}
       ::p/env      (assoc env :db (:db-after tx-report))
       :proposal/id real-id})
    (throw (ex-info "User is not logged in!" {}))))

(defmutation add-opinion [{:keys [conn AUTH/user-id] :as env} {:keys [proposal/id opinion]}]
  {::pc/params [:proposal/id :opinion]}
  (if user-id
    (let [tx-report (d/transact conn
                      [[:db/add [:proposal/id id] :proposal/opinions "temp"]
                       {:db/id          "temp"
                        :opinion/user  [:user/id user-id]
                        :opinion/value opinion}])]
      {::p/env (assoc env :db (:db-after tx-report))})
    (throw (ex-info "User is not logged in!" {}))))

(defresolver resolve-proposal-opinions [{:keys [db]} {:keys [proposal/id]}]
  {::pc/input  #{:proposal/id}
   ::pc/output [:proposal/pro-votes :proposal/con-votes]}
  (let [opinions
        (d/q
          '[:find (clojure.core/frequencies ?values) .
            :in $ ?id
            :where
            [?e :proposal/id ?id]
            [?e :proposal/opinions ?opinions]
            [?opinions :opinion/value ?values]]
          db id)]
    #:proposal{:pro-votes (get opinions 1 0)
               :con-votes (get opinions -1 0)}))


(defresolver resolve-personal-opinion [{:keys [db AUTH/user-id]} {:keys [proposal/id]}]
  {::pc/input  #{:proposal/id}
   ::pc/output [:proposal/opinion]}
  (let [opinion-value
        (d/q
          '[:find ?value .
            :in $ ?id ?user
            :where
            [?e :proposal/id ?id]
            [?e :proposal/opinions ?opinions]
            [?opinion :opinion/user ?user]
            [?opinion :opinion/value ?value]]
          db id [:user/id user-id])]
    #:proposal{:opinion (or opinion-value 0)}))

(defresolver proposal-resolver [{:keys [db]} {:keys [proposal/id]}]
  {::pc/input  #{:proposal/id}
   ::pc/output [:proposal/id :proposal/title :proposal/body :proposal/created {:proposal/parents [:proposal/id]}]}
  (let [{:proposal/keys [id title body created parents]}
        (d/pull db [:proposal/id :proposal/title :proposal/body :proposal/created
                    {:proposal/parents [:proposal/id]}]
          [:proposal/id id])]
    #:proposal{:id id :title title :body body :created created :parents (or parents [])}))

(defresolver all-proposal-ids [{:keys [db]} _]
  {::pc/input  #{}
   ::pc/output [{:all-proposals [:proposal/id]}]}
  {:all-proposals
   (for [id (d/q '[:find [?id ...] :where [_ :proposal/id ?id]] db)]
     {:proposal/id id})})



(def resolvers [add-proposal proposal-resolver all-proposal-ids resolve-proposal-opinions add-opinion resolve-personal-opinion])