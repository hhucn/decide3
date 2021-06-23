(ns decide.ui.translations.selector
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [decide.ui.meta :as meta]
    [material-ui.inputs :as inputs]
    [material-ui.inputs.form :as form]
    [material-ui.inputs.input :as input]))

(defsc LocaleSwitcher [this {:keys [::i18n/current-locale]}]
  {:query [{[::i18n/current-locale '_] (comp/get-query i18n/Locale)}]
   :initial-state {}}
  (form/control {:size :small :fullWidth true}
    (input/label {:htmlFor "language-select"} (i18n/trc "Lable for language switcher" "Language"))
    (inputs/native-select
      {:value (name (::i18n/locale current-locale :en))
       :onChange (fn [e]
                   (let [locale (keyword (evt/target-value e))]
                     (comp/transact! this [(i18n/change-locale {:locale locale})
                                           (meta/set-meta {:lang (str locale)})]
                       {:optimistic? false})))
       :inputProps {:id "language-select"}}
      (dom/option {:value "en"} "English")
      (dom/option {:value "de"} "Deutsch"))))

(def ui-language-switcher (comp/factory LocaleSwitcher))