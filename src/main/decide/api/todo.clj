(ns decide.api.todo
  (:require
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [taoensso.timbre :as log]
    [decide.models.todo :as todo]
    [decide.models.user :as user]
    [datahike.api :as d]))

(defn access-to-todo!? [db user-id todo-id]
  (let [[todo] (d/q '[:find [(pull ?e [::todo/id {::todo/owner [::user/id]}])]
                      :in $ ?todo-id
                      :where
                      [?e ::todo/id ?todo-id]] db todo-id)
        owner-id (get-in todo [::todo/owner ::user/id])]
    (cond
      (nil? user-id) (throw (ex-info "User is not logged in!" {}))
      (nil? todo) (throw (ex-info "Todo does not exist." {::todo/id todo-id}))
      (not= user-id owner-id) (throw (ex-info "User is not owner of todo" {::todo/id todo-id}))
      :else true)))


(defmutation toggle-todo [{:keys [db conn AUTH/user-id] :as env} {:todo/keys [id done?]}]
  {::pc/params [:todo/id]}
  (when (access-to-todo!? db user-id id)
    (let [tx-report (todo/update-todo! conn id {::todo/done? done?})]
      {::p/env (assoc env :db (:db-after tx-report))})))

(defmutation edit-todo [{:keys [db conn AUTH/user-id] :as env} {:todo/keys [id task]}]
  {::pc/params [:todo/id]}
  (when (access-to-todo!? db user-id id)
    (let [tx-report (todo/update-todo! conn id {::todo/task task})]
      {::p/env (assoc env :db (:db-after tx-report))})))

(defmutation add-todo [{:keys [conn AUTH/user-id] :as env} {:keys [todo]}]
  {::pc/params [:todo]
   ::pc/output [:todo/id]}
  (if user-id
    (let [real-id (todo/real-id)
          {:todo/keys [id task done?]} todo
          new-todo #::todo{:id real-id :task task :done? done? :owner [::user/id user-id]}
          tx-report (d/transact conn [new-todo])]
      {:tempids {id real-id}
       ::p/env  (assoc env :db (:db-after tx-report))
       :todo/id real-id})
    (throw (ex-info "User is not logged in!" {}))))

(defmutation delete-todo [{:keys [db conn AUTH/user-id]} {:keys [todo/id]}]
  {::pc/params [:todo/id]}
  (when (access-to-todo!? db user-id id)
    (todo/delete-todo! conn id)
    nil))

(defresolver all-todo-ids [{:keys [db AUTH/user-id]} _]
  {::pc/input  #{}
   ::pc/output [{:all-todos [:todo/id]}]}
  (if user-id
    (let [todo-ids (todo/all-todo-ids-for-user db user-id)]
      {:all-todos (map (partial hash-map :todo/id) todo-ids)})
    (throw (ex-info "User is not logged in!" {}))))

(defresolver todo-resolver [{:keys [db AUTH/user-id]} {:keys [todo/id]}]
  {::pc/input  #{:todo/id}
   ::pc/output [:todo/id :todo/task :todo/done?]}
  (when (access-to-todo!? db user-id id)
    (let [{::todo/keys [id task done?]} (todo/get-todo db id)]
      #:todo{:id id :task task :done? done?})))

;; Do not forget to add everything here
(def resolvers [add-todo all-todo-ids toggle-todo todo-resolver delete-todo edit-todo])