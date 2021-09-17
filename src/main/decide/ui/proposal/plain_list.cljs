(ns decide.ui.proposal.plain-list
  (:require
    [com.fulcrologic.fulcro.algorithms.react-interop :as interop]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [decide.models.proposal :as proposal]
    [decide.ui.components.flip-move :as flip-move]
    [decide.ui.proposal.card :as proposal-card]
    [mui.layout.grid :as grid]))

(def flip-move-item
  (js/React.forwardRef
    (fn [props ref]
      (grid/item {:xs 12 :md 6 :lg 4 :style {:flexGrow 1} :ref ref}
        (.-children props)))))

(def ui-flip-move-item (interop/react-factory flip-move-item))

(defsc PlainList [_ {:keys [items card-props]}]
  (apply flip-move/flip-move {:typeName nil}
    (for [{id ::proposal/id :as proposal} items]
      (ui-flip-move-item {:key id}
        (proposal-card/ui-proposal-card proposal card-props)))))

(def plain-list (comp/factory PlainList))