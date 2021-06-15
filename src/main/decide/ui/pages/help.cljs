(ns decide.ui.pages.help
  (:require
    [com.fulcrologic.fulcro-i18n.i18n :as i18n]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom :refer [i br]]
    [decide.utils.breakpoint :as breakpoint]
    [material-ui.data-display :as dd]
    [material-ui.inputs :as inputs]
    [material-ui.lab.alert :as alert]
    [material-ui.layout :as layout]
    [material-ui.layout.grid :as grid]
    [material-ui.surfaces :as surfaces]
    ["@material-ui/icons/Mail" :default MailIcon]))

(defn content [& content]
  (apply dd/typography {:paragraph true #_#_:style {:white-space :pre-line}}
    (interpose " " content)))

(defn header [s]
  (dd/typography {:variant :h4, :component :h2, :paragraph true, :id s} s))

(def decide (i {} "decide"))

(defn help-page []
  (comp/fragment
    (dd/typography {:variant :h2 :component :h1 :paragraph true}
      "Anleitung")
    (grid/container {}
      (grid/item {:xs 12}
        (content
          decide
          " ist eine Platform, mit der Entscheidungen für große Gruppen von Teilnehmern abgestimmt werden können.
        Jeder Teilnehmer hat die Möglichkeit eigene Vorschläge einzubringen, welche dann zur Wahl stehen."))

      (grid/item {:xs 12}
        (header "Vorschläge")
        (content
          "Die Hauptfunktion während eines Entscheidungsprozesses ist das Erstellen von neuen Vorschlägen."
          "Ein Vorschlag beinhaltet einen kurzen, aussagekräftigen Titel und eine detailliertere Beschreibung.")
        (content
          "Im Vergleich zu üblichen Systemen, in denen Vorschläge gemacht werden können erlaubt es " decide "
        Verwandtschaften zwischen Vorschlägen abzubilden."
          "Hat jemand also eine Idee, wie ein Vorschlag verbessert werden kann, kann basierend auf dem Vorschlag ein
          neuer gemacht werden.")
        (content
          "Neue Vorschläge können von mehr als einem Vorschlag abgeleitet werden. Dies ermöglicht es, ähnliche
          Vorschläge zusammenzuführen oder sogar Koalitionen mit fremden Vorschlägen einzugehen."))

      (grid/item {:xs 12}
        (header "Abstimmung")
        (content
          "Für jeden Vorschlag kann eine Zustimmung abgegeben werden."
          "Die Anzahl der Zustimmungen sind sofort öffentlich."
          "So kann direkt gesehen werden kann, wofür sich andere Teilnehmer:innen interessieren."
          "Dies hilft dabei nützliche Kompromisse vorzuschlagen.")
        (content
          "Wenn man einen neuen Vorschlag erstellt, stimmt man diesem automatisch zu."
          "Stimmen werden nicht automatisch zwischen Kindern und Eltern bewegt.")
        (content
          "Eine Zustimmung kann jederzeit wieder zurückgezogen werden, solange der Prozess noch nicht zu Ende ist."))

      (grid/item {:xs 12}
        (header "Argumente / Kommentare")
        (content
          "Es gibt die Möglichkeit Argumente zu einem Vorschlag hinzuzufügen. Diese Argumente sollen möglichst kurze,
        aussagekräftige Inhalte sein und können speziell als Pro oder Contra Argument für/gegen einen Vorschlag sein.")
        (content
          "Es ist außerdem auch möglich Argumente zu einem anderen Argument hinzuzufügen. Dadurch lassen sich beliebig
        lange Argumentationsstränge bauen."))

      (grid/item {:xs 12}
        (header "Ende des Prozesses")
        (content
          "Der Entscheidungsprozess endet nach einem festgelegten Zeitpunkt.
        Nach diesem ist es nicht mehr möglich neue Vorschläge und Kommentare hinzuzufügen oder abzustimmen.")
        (content
          "Der Gewinner des Prozesses ist der Vorschlag mit der meisten Zustimmung.
        In dem Fall, dass mehrere Vorschläge dieselbe Menge an Zustimmung haben, gewinnt der jüngste Vorschlag."))

      (grid/item {:xs 12}
        (header "Kontakt")
        (content
          "Bei Fragen oder Anmerkungen sind wir per E-Mail erreichbar:")
        (inputs/button
          {:color :primary
           :variant :contained
           :component :a
           :href "mailto:decide+help@hhu.de"
           :startIcon (dom/create-element MailIcon)}
          "Mail")))))

(defsc InstructionPage [_ {[_ locale] ::i18n/current-locale}]
  {:query [::i18n/current-locale]
   :ident (fn [] [:PAGE ::InstructionPage])
   :initial-state {}
   :route-segment ["help"]
   :use-hooks? true}
  (layout/box {:mt 2 :clone true}
    (layout/container
      {:maxWidth :md
       :disableGutters (breakpoint/<=? "xs")}
      (surfaces/card {}
        (alert/alert {:severity :warning}
          (i18n/tr "The instructions are currently only available in german. Please send us a mail via the button at
          the end of this page, if you have questions."))
        (surfaces/card-content {}
          (case locale
            :de (help-page)
            (help-page)))))))