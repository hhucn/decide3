(ns decide.application
  (:require
    [com.fulcrologic.fulcro-css.css :as css]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.rad.application :as rad-app]))

(defonce SPA
  (rad-app/fulcro-rad-app
    {:global-eql-transform
     (-> rad-app/default-network-blacklist
       (conj :root/current-session)
       rad-app/elision-predicate
       rad-app/global-eql-transform)

     :props-middleware
     (comp/wrap-update-extra-props
       (fn [cls extra-props]
         (merge extra-props (css/get-classnames cls))))}))