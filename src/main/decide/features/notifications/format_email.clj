(ns decide.features.notifications.format-email
  (:require
    [clojure.string :as str]
    [decide.models.process :as process]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]
    [hiccup.page :refer [html5]]))

(defn format-argument [{:keys [argument/premise arguments new? argument/type]}]
  [:li
   [:p (when new? [:span.red "NEW "])
    (case type
      :pro [:span.pro "Pro: "]
      :contra [:span.pro "Contra: "]
      "")

    (:statement/content premise)]
   (when (seq? arguments)
     [:ul (map format-argument arguments)])])

(defn format-proposal [{{::proposal/keys [id title] :keys [new?]} :proposal
                        :keys [arguments] :as p} {:keys [base-url]}]
  [:li
   [:p
    (when new? [:span.red "NEW "])
    [:a {:href (str base-url "/proposal/" id)} title]]
   (when-not (empty? arguments)
     (list
       [:p "Arguments:"]
       [:ul
        (map format-argument arguments)]))])

(defn format-process [{::process/keys [slug title]
                       :keys [proposals]}
                      {:keys [base-url] :as payload}]
  [:li
   [:p.list-header
    [:a {:href (str base-url "/decision/" slug "/home")} title]]
   [:p "Proposals:"]
   [:ul
    (map #(format-proposal % (update payload :base-url (fn [base] (str base "/decision/" slug))))
      proposals)]])

(defn format-email [{:keys [user] :as payload}]
  (html5
    [:html
     [:head
      [:style
       ".red {color: red;}"]]
     [:body
      [:p "Hi " (::user/display-name user) ",\n\nthere is something new on decide!\n"]
      [:ul
       (for [process (:processes payload)]
         (format-process process payload))]]]))

(defn make-message [{:keys [user] :as payload}]
  {:to (if (some-> user :user/email (str/includes? "@"))
         (:user/email user)
         "ebbinghaus@hhu.de")
   :from "decide <decide@hhu.de>"
   :subject "News from decide!"
   :body [{:content (format-email payload)
           :type "text/html; charset=utf-8"}]})
