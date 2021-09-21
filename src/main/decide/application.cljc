(ns decide.application
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.rad.application :as rad-app]
    #?(:cljs ["intl-messageformat" :default IntlMessageFormat])))

#?(:cljs
   (defn message-formatter [{:keys [::i18n/localized-format-string ::i18n/locale ::i18n/format-options]}]
     (let [locale-str (name locale)
           formatter (IntlMessageFormat. localized-format-string locale-str)]
       (.format formatter (clj->js format-options)))))

(defonce SPA
  (rad-app/fulcro-rad-app
    {:global-eql-transform
     (-> rad-app/default-network-blacklist
       (conj :root/current-session)
       rad-app/elision-predicate
       rad-app/global-eql-transform)

     #?@(:cljs
         [:shared {::i18n/message-formatter message-formatter}])

     :shared-fn
     (fn shared-fn [db]
       (merge
         {:logged-in? (get-in db [:root/current-session :session/valid?] false)}
         (::i18n/current-locale db)))}))