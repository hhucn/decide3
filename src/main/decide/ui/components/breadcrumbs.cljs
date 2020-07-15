(ns decide.ui.components.breadcrumbs
  (:require
    [material-ui.navigation :as navigation]
    [material-ui.layout :as layout]))

(defn breadcrumb-nav
  [crumbs]
  (layout/box {:my 1.5}
    (navigation/breadcrumbs {:aria-label "breadcrumb"}
      (for [[label href] (butlast crumbs)]
        (navigation/link {:color "inherit"
                          :href  href}
          label))
      (let [[label href] (last crumbs)]
        (navigation/link {:color "textPrimary" :href href} label)))))