(ns mock-data
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [datahike.api :as d]
   [decide.models.opinion :as opinion]
   [decide.models.process :as process]
   [decide.models.proposal :as proposal]
   [decide.models.user :as user]))


(defn gen-user []
  (gen/generate (s/gen (s/keys :req [::user/id ::user/display-name]))))


(defn gen-proposal []
  (gen/generate (s/gen (s/keys :req [::proposal/id ::proposal/title ::proposal/body ::proposal/created]))))


(defn gen-process []
  (gen/generate (s/gen (s/keys :req [::process/slug ::process/title ::process/description]))))


(defn add-user [process]
  (let [new-user (gen-user)
        new-user (assoc new-user :db/id (str "user-" (::user/id new-user)))]
    (update process :users assoc (::user/id new-user) new-user)))


(defn random-proposal [process]
  (rand-nth (vals (:proposals process))))


(defn coin-toss []
  (<= 0.5 (rand)))


(defn add-proposal [process]
  (if-let [author (rand-nth (vals (:users process)))]
    (let [nice-id      (inc (count (:proposals process)))
          parent1      (random-proposal process)
          parent2      (random-proposal process)

          new-proposal (gen-proposal)
          new-proposal
                       (merge new-proposal
                         #::proposal{:db/id (str "proposal-" (::proposal/id new-proposal))
                                     :original-author (:db/id author)
                                     :nice-id nice-id})

          new-proposal
                       (if (and (coin-toss) (some? parent1))
                         (if (and (coin-toss) (some? parent2) (not= parent1 parent2))
                           (assoc new-proposal ::proposal/parents [(:db/id parent1) (:db/id parent2)])
                           (assoc new-proposal ::proposal/parents [(:db/id parent1)]))
                         new-proposal)]

      (update process :proposals assoc (::proposal/id new-proposal) new-proposal))
    process))


(defn add-approval [process]
  (if-let [user (rand-nth (vals (:users process)))]
    (if-let [proposal (random-proposal process)]
      (let [opinion #::opinion{:value +1}]
        (update process :opinions assoc [(::user/id user) (::proposal/id proposal)] opinion))
      process)
    process))


(defn increase-scale [process]
  (let [op (rand-nth [add-user add-proposal add-approval add-approval add-approval])]
    (op process)))


(defn gen-mock-process [iterations]
  (nth (iterate increase-scale {}) iterations))


(defn mock-process->tx [mock-process]
  (let [{:keys [users proposals opinions]} mock-process]
    (vec
      (concat
        [(merge
           (gen-process)
           #::process{:participants (vec (vals users))
                      :proposals (vec (vals proposals))
                      :type ::process/type.public})]
        (mapcat
          (fn [[[user-id proposal-id] opinion]]
            (let [temp-id (str "opinion-" user-id proposal-id)]
              [(assoc opinion :db/id temp-id)
               [:db/add (str "user-" user-id) ::user/opinions temp-id]
               [:db/add (str "proposal-" proposal-id) ::proposal/opinions temp-id]]))
          opinions)))))


(defn add-mock-process! [conn scale]
  (d/transact conn
    {:tx-data
     (mock-process->tx (gen-mock-process scale))}))

