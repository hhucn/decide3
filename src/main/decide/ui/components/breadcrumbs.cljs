(ns decide.ui.components.breadcrumbs
  (:require
    [cljs.spec.alpha :as s]
    [clojure.string :as str]
    [com.fulcrologic.fulcro.dom :as dom]
    [ghostwheel.core :refer [>defn =>]]
    [material-ui.navigation :as navigation]
    [material-ui.layout :as layout]))

(s/def ::label (s/and string? (complement str/blank?)))
(s/def ::href string?)
(s/def ::breadcrumb (s/tuple ::label ::href))

(>defn breadcrumb-nav
  [crumbs]
  [(s/coll-of ::breadcrumb :kind sequential? :distinct true) => dom/element?]
  (layout/box {:pt 1.5 :clone true}
    (navigation/breadcrumbs {:aria-label "breadcrumb"}
      (for [[label href] (butlast crumbs)]
        (navigation/link {:key   [label href]
                          :color "inherit"
                          :href  href}
          label))
      (let [[label href] (last crumbs)]
        (navigation/link {:key   [label href]
                          :color "textPrimary"
                          :href  href}
          label)))))