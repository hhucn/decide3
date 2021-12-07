(ns decide.specs.process
  (:require
    [#?(:default clojure.spec.alpha
        :cljs    cljs.spec.alpha) :as s]
    [decide.specs.common :as common]
    [decide.user :as user]))

(def slug-pattern #"^[a-z0-9]+(?:[-_][a-z0-9]+)*$")
(s/def :decide.process/slug (s/and string? #(re-matches slug-pattern %)))
(s/def :decide.process/title ::common/non-blank-string)
(s/def :decide.process/description string?)
(s/def :decide.process/latest-id pos-int?)
(s/def :decide.process/end-time inst?)
(s/def :decide.process/start-time inst?)
(s/def :decide.process/type #{:decide.process/type.public :decide.process/type.private})
(s/def :decide.process/feature
  #{;;Participants may only approve to a single proposal.
    :decide.process/feature.single-approve

    :process/feature.voting-public
    :process/feature.voting-show-nothing

    ;; Participants will be able to reject a proposal.
    :decide.process/feature.rejects
    ;; Ask the participant to give a reason for a reject.
    :decide.process/feature.reject-popup})
(s/def :decide.process/features (s/coll-of :decide.process/feature))
(s/def :decide.process/moderators (s/coll-of (s/keys :req [::user/id])))
(s/def :decide.process/participants (s/coll-of (s/keys :req [::user/id])))

