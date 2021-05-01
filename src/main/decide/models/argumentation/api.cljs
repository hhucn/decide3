(ns decide.models.argumentation.api
  (:require
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]))

(defmutation add-argument-to-statement [{:keys [conclusion]
                                         {premise :argument/premise} :argument :as argument}]
  (remote [_] true))

(defmutation add-argument-to-proposal [{:keys [proposal]
                                        {premise :argument/premise} :argument :as argument}]
  (remote [_] true))