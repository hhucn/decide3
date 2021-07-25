(ns decide.models.argumentation.api
  (:require
    [com.fulcrologic.fulcro.algorithms.merge :as mrg]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.raw.components :as rc]))

(def Argument (rc/nc [:argument/id :argument/no-of-arguments :argument/type
                      {:argument/premise->arguments '...}
                      {:argument/premise
                       [:statement/id :statement/content
                        {:statement/author [:decide.models.user/id]}]}]))

(defn- complete-new-argument [{:keys [state]} argument]
  (let [user (get-in @state [:root/current-session :user])]
    (-> argument
      (assoc :argument/no-of-arguments 0)
      (update :argument/premise
        assoc :statement/author user))))


(defmutation add-argument-to-statement [{:keys [conclusion]
                                         {premise :argument/premise :as argument} :argument}]
  (action [{:keys [app ref] :as env}]
    (mrg/merge-component! app
      Argument
      (complete-new-argument env argument)
      :append (conj ref :argument/premise->arguments)))
  (remote [_] true))

(defmutation add-argument-to-proposal [{:keys [proposal]
                                        {premise :argument/premise :as argument} :argument}]
  (action [{:keys [app ref] :as env}]
    (mrg/merge-component! app Argument
      (complete-new-argument env argument)
      :append (conj ref :decide.models.proposal/positions)))
  (remote [_] true))