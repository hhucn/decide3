(ns decide.ui.components.flip-move
  (:require
    ["react-flip-move" :default FlipMove]
    [com.fulcrologic.fulcro.algorithms.react-interop :as interop]))

(def
  ^{:arglists '([{:keys [easing duration delay staggerDurationBy staggerDelayBy appearAnimation enterAnimation leaveAnimation
                         maintainContainerHeight verticalAlignment onStart onFinish onStartAll onFinishAll typeName
                         disableAllAnimations getPosition]}
                 & children])}
  flip-move (interop/react-factory FlipMove))