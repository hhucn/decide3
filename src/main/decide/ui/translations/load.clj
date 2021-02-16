(ns decide.ui.translations.load
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.wsscode.pathom.connect :as pc]))

(pc/defresolver locale-resolver [{{{:keys [locale]} :params} :ast} _]
  {::pc/output [::i18n/translations]}
  (when-let [translations (i18n/load-locale "po-files" locale)]
    {::i18n/translations translations}))
