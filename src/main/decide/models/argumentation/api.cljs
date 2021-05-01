(ns decide.models.argumentation.api
  (:require
    [com.fulcrologic.fulcro.algorithms.merge :as mrg]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [taoensso.timbre :as log]))


(defmutation add-argument-to-statement [{:keys [conclusion]
                                         {premise :argument/premise :as argument} :argument}]
  (action [{:keys [app component ref]}]
    (log/info component)
    (mrg/merge-component! app
      component
      argument
      :append (conj ref :argument/premise->arguments)))
  (remote [_] true))

(defmutation add-argument-to-proposal [{:keys [proposal]
                                        {premise :argument/premise :as argument} :argument}]
  (action [{:keys [app ref]}]
    (mrg/merge-component! app
      (comp/registry-key->class 'decide.models.argumentation.ui/Argument)
      argument
      :append (conj ref :decide.models.proposal/positions)))
  (remote [_] true))