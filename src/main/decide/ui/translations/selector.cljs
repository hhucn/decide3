(ns decide.ui.translations.selector
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [decide.models.authorization :as auth]
    [decide.models.user :as user]
    [decide.models.user.api :as user.api]
    [decide.ui.meta :as meta]
    [mui.inputs :as inputs]
    [mui.inputs.form :as form]
    [mui.inputs.input :as input]
    [mui.navigation :as navigation]))

(defmutation change-language [{:keys [locale]}]
  (action [{:keys [component state]}]
    (when-let [user-id (::user/id (auth/current-user @state))]
      (comp/transact! component
        [(user.api/update-user {::user/id user-id
                                :user/language locale})]))))

(defsc LocaleSwitcher [this {:keys [::i18n/current-locale]}]
  {:query [{[::i18n/current-locale '_] (comp/get-query i18n/Locale)}]
   :initial-state {}}
  (form/control {:fullWidth true, :size :small}
    (input/label {:id "language-select"} (i18n/trc "Lable for language switcher" "Language"))
    (inputs/select
      {:value (name (::i18n/locale current-locale :en))
       :onChange #(when-let [locale (keyword (evt/target-value %))]
                    (comp/transact! this [(change-language {:locale locale})
                                          (i18n/change-locale {:locale locale})
                                          (meta/set-meta {:lang (name locale)})]))
       :inputProps {}
       :labelId "language-select"
       :label (i18n/trc "Lable for language switcher" "Language")}
      (navigation/menu-item {:value "en"} "English")
      (navigation/menu-item {:value "de"} "Deutsch"))))

(def ui-language-switcher (comp/factory LocaleSwitcher))