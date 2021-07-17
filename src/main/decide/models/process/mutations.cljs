(ns decide.models.process.mutations
  (:require
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro.algorithms.merge :as mrg]
    [com.fulcrologic.fulcro.algorithms.normalized-state :as norm-state]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.raw.components :as rc]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]
    [taoensso.timbre :as log]))

(defsc Process [_ _]
  {:query
   [::process/slug
    ::process/title
    ::process/description
    ::process/end-time
    ::process/type
    {::process/proposals (rc/get-query proposal/Proposal)}]
   :ident ::process/slug})

(defmutation add-proposal [{::proposal/keys [_id _title _body _parents]
                            ::process/keys [slug]
                            :as params}]
  (action [{:keys [app]}]
    (mrg/merge-component! app proposal/Proposal params
      :append (conj (rc/get-ident Process {::process/slug slug}) ::process/proposals)))
  (remote [env]
    (m/returning env proposal/Proposal)))


(defmutation add-process [{::process/keys [slug title description] :as process}]
  (action [{:keys [app]}]
    (mrg/merge-component! app Process process))
  (remote [env]
    (-> env
      (m/returning Process)
      (m/with-target (targeting/append-to [:root/all-processes])))))

(defmutation update-process [{::process/keys [slug] :as process}]
  (action [{:keys [app]}]
    (when slug
      (mrg/merge-component! app Process process)))
  (remote [env]
    (-> env
      (m/returning Process))))

(defmutation add-moderator [{::process/keys [slug] email ::user/email}]
  (remote [env]
    (-> env
      (m/with-target (targeting/append-to [::process/slug slug ::process/moderators]))
      (m/returning user/User))))

(defn- refresh-no-of-participants* [state process-ident]
  (update-in state process-ident
    (fn [{::process/keys [participants] :as process}]
      (cond-> process
        participants (assoc ::process/no-of-participants (or (count participants) 0))))))

(defmutation add-participant [{user-id ::user/id slug ::process/slug :as params}]
  (action [{:keys [state]}]
    (if (and user-id slug)
      (norm-state/swap!-> state
        (norm-state/integrate-ident [::user/id user-id] :prepend [::process/slug slug ::process/participants])
        (refresh-no-of-participants* [::process/slug slug]))
      (log/error `add-participant "needs a" ::process/slug "and a" ::user/id "! Params:" params)))
  (remote [_] (boolean (and user-id slug))))

(defmutation remove-participant [{user-id ::user/id slug ::process/slug :as params}]
  (action [{:keys [state]}]
    (if (and user-id slug)
      (norm-state/swap!-> state
        (norm-state/remove-ident [::user/id user-id] [::process/slug slug ::process/participants])
        (refresh-no-of-participants* [::process/slug slug]))
      (log/error `remove-participant "needs a" ::process/slug "and a" ::user/id "! Params:" params)))
  (remote [_] (boolean (and user-id slug))))

