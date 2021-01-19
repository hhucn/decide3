(ns decide.ui.session)

(def context (js/React.createContext #js {:user "bla"}))

(defn logged-in? [ctx] (boolean (:decide.models.user/id ctx)))