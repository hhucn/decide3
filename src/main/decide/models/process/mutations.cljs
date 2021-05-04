(ns decide.models.process.mutations
  (:require
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro.algorithms.merge :as mrg]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]))

(defsc Process [_ _]
  {:query
   [::process/slug
    ::process/title
    ::process/description
    ::process/end-time
    ::process/type
    {::process/proposals (comp/get-query proposal/Proposal)}]
   :ident ::process/slug})

(defmutation add-proposal [{::proposal/keys [_id _title _body _parents]
                            ::process/keys [slug]
                            :as params}]
  (action [{:keys [app]}]
    (mrg/merge-component! app proposal/Proposal params
      :append (conj (comp/get-ident Process {::process/slug slug}) ::process/proposals)))
  (remote [env]
    (m/returning env proposal/Proposal)))


(defmutation add-process [{::process/keys [slug title description] :as process}]
  (action [{:keys [app]}]
    (mrg/merge-component! app Process process))
  (remote [env]
    (-> env
      (m/returning Process)
      (m/with-target (targeting/append-to [:all-processes])))))

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