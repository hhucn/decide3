(ns decide.process
  (:require
    [clojure.set :as set]
    [#?(:default clojure.spec.alpha
        :cljs    cljs.spec.alpha) :as s]
    [com.fulcrologic.guardrails.core :refer [>def >defn >defn- => | ? <-]]
    [decide.specs.common :as common]
    [decide.user :as user]
    [decide.utils.slugify :as slugify]))

(def features
  #{;;Participants may only approve to a single proposal.
    ::feature.single-approve

    ::feature.voting.public
    ::feature.voting.show-nothing

    ;; Participants will be able to reject a proposal.
    ::feature.rejects
    ;; Ask the participant to give a reason for a reject.
    ::feature.reject-popup})

(>def ::slug ::slugify/slug)
(>def ::title ::common/non-blank-string)
(>def ::description string?)
(>def ::latest-id pos-int?)
(>def ::end-time inst?)
(>def ::start-time inst?)
(>def ::type #{::type.public ::type.private})
(>def ::feature
  (set/union
    features
    ;; legacy keys
    #{:process/feature.voting-public
      :process/feature.voting-show-nothing}))
(>def ::features (s/coll-of ::feature))
(>def ::moderators (s/coll-of (s/keys :req [::user/id])))
(>def ::participants (s/coll-of (s/keys :req [::user/id])))

(defn now [])

(defn participant? [process user]
  (contains? (::participants process) user))

(defn moderator? [process user]
  (contains? (::moderators process) user))

(defn running? [process]
  (< (::start-time process) (now) (::end-time process)))

(defn make
  "Makes a new process"
  [{}]
  {::id 42})