(ns decide.models.process
  (:require
    [cljs.spec.alpha :as s]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro.algorithms.merge :as mrg]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.guardrails.core :refer [>defn => ? | <-]]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]
    [decide.ui.common.time :as time]))

(s/def ::end-time inst?)

(defsc Process [_ _]
  {:query
   [::slug
    ::title
    ::description
    ::end-time
    {::proposals (comp/get-query proposal/Proposal)}]
   :ident ::slug})

(>defn over? [{::keys [end-time]}]
  [(s/keys :req [::end-time]) => boolean?]
  (if end-time
    (time/in-past? end-time)
    false))

(defmutation add-proposal [{::proposal/keys [_id _title _body _parents]
                            ::keys [slug]
                            :as params}]
  (action [{:keys [app]}]
    (mrg/merge-component! app proposal/Proposal params
      :append (conj (comp/get-ident Process {::slug slug}) ::proposals)))
  (remote [env] (m/returning env proposal/Proposal)))


(defmutation add-process [{::keys [slug title description] :as process}]
  (action [{:keys [app]}]
    (mrg/merge-component! app Process process))
  (remote [env]
    (-> env
      (m/returning Process)
      (m/with-target (targeting/append-to [:all-processes])))))

(defmutation update-process [{::keys [slug] :as process}]
  (action [{:keys [app]}]
    (when slug
      (mrg/merge-component! app Process process)))
  (remote [env]
    (-> env
      (m/returning Process))))

(defmutation add-moderator [{::keys [slug] email ::user/email}]
  (remote [env]
    (-> env
      (m/with-target (targeting/append-to [::slug slug ::moderators]))
      (m/returning user/User))))
