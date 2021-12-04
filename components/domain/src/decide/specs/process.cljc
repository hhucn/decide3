(ns decide.specs.process
  (:require
    [#?(:default clojure.spec.alpha
        :cljs    cljs.spec.alpha) :as s]
    [clojure.string :as str]
    [decide.process :as process]
    [decide.user :as user]
    [decide.specs.user]
    [decide.specs.common :as common]))

(def slug-pattern #"^[a-z0-9]+(?:[-_][a-z0-9]+)*$")
(s/def ::process/slug (s/and string? (partial re-matches slug-pattern)))
(s/def ::process/title ::common/non-blank-string)
(s/def ::process/description string?)
(s/def ::process/latest-id pos-int?)
(s/def ::process/end-time inst?)
(s/def ::process/start-time inst?)
(s/def ::process/type #{::process/type.public ::process/type.private})
(s/def ::process/feature
  #{;;Participants may only approve to a single proposal.
    :process.feature/single-approve

    :process.feature/voting.public
    :process.feature/voting.show-nothing

    ;; Participants will be able to reject a proposal.
    :process.feature/rejects
    ;; Ask the participant to give a reason for a reject.
    :process.feature/reject-popup})
(s/def ::process/features (s/coll-of ::process/feature))
(s/def ::process/moderators (s/coll-of (s/keys :req [::user/id])))

