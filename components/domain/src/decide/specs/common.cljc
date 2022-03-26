(ns decide.specs.common
  (:require
    [#?(:clj  clojure.spec.alpha
        :cljs cljs.spec.alpha)
     :as s]
    [clojure.string :as str]
    #?(:clj [clojure.spec.gen.alpha :as gen])))

;; see https://emailregex.com/ after RFC 5322
(def email-pattern
  #?(:clj  #"(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21\x23-\x5b\x5d-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21-\x5a\x53-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])+)\])"
     :cljs #"^(([^<>()\[\]\\.,;:\s@\"]+(\.[^<>()\[\]\\.,;:\s@\"]+)*)|(\".+\"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$"))


;; from https://gist.github.com/conan/2edca210999b96ad26d38c1ee96dfe40
#?(:clj
   (def non-empty-string-alphanumeric
     "Generator for non-empty alphanumeric strings"
     (gen/such-that #(not= "" %)
       (gen/string-alphanumeric))))

#?(:clj
   (def email-gen
     "Generator for email addresses"
     (gen/fmap
       (fn [[name host tld]]
         (str name "@" host "." tld))
       (gen/tuple
         non-empty-string-alphanumeric
         non-empty-string-alphanumeric
         non-empty-string-alphanumeric))))

(defn email? [email]
  (boolean (re-matches email-pattern email)))

;;; Specs
(s/def ::email #?(:clj  (s/with-gen email? email-gen)
                  :cljs (s/and string? email?)))
(s/def ::non-blank-string (s/and string? (complement str/blank?)))

(s/def :db/id pos-int?)