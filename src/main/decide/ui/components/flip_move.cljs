(ns decide.ui.components.flip-move
  (:require
    ["react-flip-move" :as FlipMove]
    [com.fulcrologic.fulcro.algorithms.react-interop :as interop]))

(def flip-move (interop/react-factory FlipMove))