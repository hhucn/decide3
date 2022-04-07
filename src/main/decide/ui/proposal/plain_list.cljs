(ns decide.ui.proposal.plain-list
  (:require
    [com.fulcrologic.fulcro.algorithms.react-interop :as interop]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [decide.ui.components.flip-move :as flip-move]
    [mui.layout.grid :as grid]
    ["react" :as react]))

(def flip-move-item
  (react/forwardRef
    (fn [props ref]
      (grid/item {:xs 12, :md 6, :lg 4
                  :style {:minHeight "100px"}
                  :ref ref}
        (.-children props)))))

(def ui-list-item (interop/react-factory flip-move-item))

(defsc PlainList [this _]
  (grid/container {:spacing {:xs 1, :sm 2}
                   :style {:position :relative}
                   :alignItems "stretch"}
    (flip-move/flip-move {:typeName nil}
      (for [child (comp/children this)
            :let [key (.-key child)]]
        (ui-list-item {:key key} child)))))

(def plain-list (comp/factory PlainList))