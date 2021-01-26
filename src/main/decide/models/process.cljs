(ns decide.models.process
  (:require
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro.algorithms.merge :as mrg]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [decide.models.proposal :as proposal]))


(defsc Process [_ _]
  {:query
   [::slug
    ::title
    ::description
    {::proposals (comp/get-query proposal/Proposal)}]
   :ident ::slug})

(defmutation add-proposal [{::proposal/keys [_id _title _body _parents]
                            ::keys [slug]
                            :as params}]
  (action [{:keys [app]}]
    (mrg/merge-component! app proposal/Proposal params
      :append (conj (comp/get-ident Process {::slug slug}) ::proposals)))
  (remote [env] (m/returning env proposal/Proposal)))


(defmutation add-process [{::keys [slug title description]}]
  (remote [env]
    (-> env
      (m/returning Process)
      (m/with-target (targeting/append-to [:all-processes])))))