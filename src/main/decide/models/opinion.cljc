(ns decide.models.opinion
  (:require
    [#?(:clj clojure.spec.alpha
        :cljs cljs.spec.alpha) :as s]
    [com.fulcrologic.guardrails.core :refer [>defn =>]]
    [decide.models.proposal :as proposal]
    [decide.models.user :as user]))

(def schema [{:db/ident       ::user/opinions
              :db/cardinality :db.cardinality/many
              :db/valueType   :db.type/ref
              :db/isComponent true}

             {:db/ident       ::proposal/opinions
              :db/cardinality :db.cardinality/many
              :db/valueType :db.type/ref
              :db/isComponent true}

             {:db/ident ::value
              :db/doc "Value of alignment of the opinion. 0 is neutral. Should be -1, 0 or +1 for now."
              :db/cardinality :db.cardinality/one
              :db/valueType :db.type/long}])

(s/def ::value #{-1 0 +1 +2})
(s/def ::opinion (s/keys :req [::value]))
(s/def ::proposal/opinions (s/coll-of ::opinion))
(s/def ::entity (s/and associative? #(contains? % :db/id)))

(def approval-value? pos?)
(def neutral-value? zero?)
(def reject-value? neg?)
(defn favorite-value? [x] (= x 2))

(defn approval? [opinion]
  (approval-value? (::value opinion)))

(defn neutral? [opinion]
  (neutral-value? (::value opinion)))

(defn reject? [opinion]
  (reject-value? (::value opinion)))

(defn favorite? [opinion]
  (favorite-value? (::value opinion)))

(>defn votes
  "Provided a proposal with opinions, enhances the proposal with a total of pro- and con-votes."
  [{::proposal/keys [opinions] :as proposal}]
  [(s/keys :opt [::proposal/opinions])
   => (s/keys :req [::proposal/pro-votes ::proposal/con-votes])]
  (let [freqs (frequencies (map ::value opinions))]
    (assoc proposal
      ::proposal/pro-votes (get freqs 1 0)
      ::proposal/con-votes (get freqs -1 0))))