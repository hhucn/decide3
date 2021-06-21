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

(s/def ::value #{-1 0 +1})
(s/def ::opinion (s/keys :req [::value]))
(s/def ::proposal/opinions (s/coll-of ::opinion))

(>defn votes [{::proposal/keys [opinions] :as proposal}]
  [(s/keys :opt [::proposal/opinions])
   => (s/keys :req [::proposal/pro-votes ::proposal/con-votes])]
  (let [freqs (frequencies (map ::value opinions))]
    (assoc proposal
      ::proposal/pro-votes (get freqs 1 0)
      ::proposal/con-votes (get freqs -1 0))))