(ns decide.test-utils.common
  (:require
   [datahike.api :as d]
   [decide.models.opinion :as-alias opinion]
   [decide.models.process :as-alias process]
   [decide.models.proposal :as proposal]
   [decide.models.user :as user]
   [decide.server-components.database :as database]
   [taoensso.timbre :as log]))


(def ^:dynamic *conn* nil)


(def test-db
  (vec
    (concat
      (for [{:keys [id name]}
            [{:id #uuid"0000fb5e-a9d0-44b6-b293-bb3c506fc0cb", :name "Björn"}
             {:id #uuid"001e7a76-9c45-49a9-865c-a631641544dd", :name "Martin"}
             {:id #uuid"0012d971-6b09-4be3-a5ac-f02f2352a235", :name "Christian"}
             {:id #uuid"001e7a7e-3eb2-4226-b9ab-36dddcf64106", :name "Markus"}
             {:id #uuid"0000004a-e4fd-420c-ba19-6de5b59c702d", :name "Jan"}
             {:id #uuid"000aa0e2-e4d6-463d-ae7c-46765e13a31b", :name "Alexander"}
             {:id #uuid"00000956-b2e2-4285-ac73-1414ec692b0c", :name "Marc"}]]
        (assoc (user/tx-map {::user/id id
                             ::user/email name
                             ::user/password name})
          :db/id name))
      [#::process{:slug "private-decision"
                  :type ::process/type.private
                  :title "Private decision"
                  :description "This decision ist private"
                  :moderators ["Björn"]
                  :participants ["Markus"]
                  :proposals
                  [#::proposal{:db/id "A"
                               :id #uuid"5fdc8014-bd58-43f6-990f-00000000000a"
                               :title "A"
                               :opinions (repeat 2 #::opinion{:value +1})}
                   #::proposal{:db/id "B"
                               :id #uuid"5fdc8014-bd58-43f6-990f-00000000000b"
                               :title "B"
                               :opinions (repeat 3 #::opinion{:value +1})}
                   #::proposal{:db/id "C"
                               :id #uuid"5fdc8014-bd58-43f6-990f-00000000000c"
                               :title "C"
                               :parents ["A"]
                               :opinions (repeat 5 #::opinion{:value +1})}
                   #::proposal{:db/id "D"
                               :id #uuid"5fdc8014-bd58-43f6-990f-00000000000d"
                               :title "D"
                               :parents ["A" "B"]
                               :opinions (repeat 3 #::opinion{:value +1})}
                   #::proposal{:title "E"
                               :id #uuid"5fdc8014-bd58-43f6-990f-00000000000e"
                               :parents ["C"]
                               :opinions (repeat 1 #::opinion{:value +1})}
                   #::proposal{:title "F"
                               :id #uuid"5fdc8014-bd58-43f6-990f-00000000000f"
                               :parents ["C" "D"]
                               :opinions (repeat 4 #::opinion{:value +1})}]}
       {::process/slug "test-decision"
        ::process/title "Meine Test-Entscheidung"
        ::process/description "Wir müssen irgendwas für die Uni entscheiden."
        ::process/latest-id 5,
        ::process/moderators ["Björn"]
        #_#_::process/end-time #inst"2021-02-01"
        ::process/proposals
        [{:db/id "Wasserspender"
          ::proposal/body "Wasser ist gesund für Studenten.",
          ::proposal/created #inst"2020-12-18T09:58:15.232-00:00",
          ::proposal/id #uuid"5fdc7d37-107b-4484-ab85-2911be84c39e",
          ::proposal/nice-id 1,
          ::proposal/original-author "Christian",
          ::proposal/title "Wir sollten einen Wasserspender aufstellen"}
         {:db/id "goldener Wasserspender"
          ::proposal/body "Wasser ist gesund für Studenten, aber wir sollten auch auf \"Qualität\" achten.",
          ::proposal/created #inst"2020-12-18T10:10:28.182-00:00",
          ::proposal/id #uuid"5fdc8014-bd58-43f6-990f-713741c81d9f",
          ::proposal/nice-id 2,
          ::proposal/original-author "Martin",
          ::proposal/parents ["Wasserspender"],
          ::proposal/title "Wir sollten einen goldenen Wasserspender aufstellen"}
         {:decide.models.proposal/id #uuid"5fddd194-2d0b-46fd-8256-73081eec95f5",
          :decide.models.proposal/parents [],
          :decide.models.proposal/body "Viele DIY Projekte lassen sich heutzutage gut mithilfe von Prototypen aus dem 3-D Drucker bewerkstelligen. Z.B. ein Gehäuse für den Raspberry PI.",
          :decide.models.proposal/nice-id 3,
          :decide.models.proposal/original-author "Jan",
          :db/id "3d-drucker",
          :decide.models.proposal/arguments [],
          :decide.models.proposal/created #inst"2020-12-19T10:10:28.182-00:00",
          :decide.models.proposal/title "Ein 3-D Drucker für DIY Projekte"}
         {:decide.models.proposal/id #uuid"5fde17e4-f2f5-40a0-bf62-cd5e40015bf9",
          :decide.models.proposal/parents [],
          :decide.models.proposal/body "Möchte man hardware-nahe Projekt entwickeln, dann benötigt man häufig einiges an Arbeitsmaterial, wie z.B. eine Lötstation oder Multimeter. Es könnte im ZIM ein Raum geschaffen werden, wo so etwas vorhanden ist.",
          :decide.models.proposal/nice-id 4,
          :decide.models.proposal/original-author "Marc",
          :db/id "hackerspace",
          :decide.models.proposal/arguments [],
          :decide.models.proposal/created #inst"2020-12-19T15:10:28.182-00:00",
          :decide.models.proposal/title "Man könnte einen Hackerspace im ZIM bauen."}
         {:decide.models.proposal/id #uuid"5fdf6964-1825-4ace-a348-ad925eea1fb5",
          :decide.models.proposal/parents ["3d-drucker" "hackerspace"],
          :decide.models.proposal/body "Möchte man hardware-nahe Projekt entwickeln, dann benötigt man häufig einiges an Arbeitsmaterial, wie z.B. eine Lötstation oder Multimeter. Es könnte im ZIM ein Raum geschaffen werden, wo so etwas vorhanden ist.

                               Hier könnte auch ein 3-D Drucker aufgestellt werden. ",
          :decide.models.proposal/nice-id 5,
          :decide.models.proposal/original-author "Markus",
          :db/id "hackerspace+3d",
          :decide.models.proposal/arguments [],
          :decide.models.proposal/created #inst"2020-12-20T15:10:28.182-00:00",
          :decide.models.proposal/title "Man könnte einen Hackerspace inkl. 3-D Drucker einrichten."}]}]
      (apply concat
        (for [[proposal users-who-agree]
              {"Wasserspender" ["Christian" "Martin" "Alexander" "Jan"]
               "goldener Wasserspender" ["Martin" "Alexander"]
               "3d-drucker" ["Martin" "Jan" "Marc" "Christian"]
               "hackerspace" ["Björn" "Jan" "Marc" "Christian" "Markus"]
               "hackerspace+3d" ["Björn" "Jan" "Marc" "Christian" "Markus"]}]
          (apply concat
            (for [user users-who-agree]
              [[:db/add proposal ::proposal/opinions (str proposal "+" user)]
               [:db/add user ::user/opinions (str proposal "+" user)]
               {:db/id (str proposal "+" user)
                ::opinion/value 1}])))))))



(defn db-fixture [initial-db]
  (fn [f]
    (binding [*conn* (log/with-level :info (database/test-database initial-db))]
      (try
        (f)
        (catch Exception e (throw e))
        (finally
          (d/release *conn*)
          (d/delete-database))))))

(def test-db-fixture (db-fixture test-db))

