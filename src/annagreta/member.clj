(ns annagreta.member "Authentication functions with minimal dependencies that relate to member handling."
  (:require [annagreta.core :as core])
  (:require [annagreta.person :as person])
  (:require [torpo.core :as util]))

(defn signs? [key member] (core/unlocks? key :sign (person/primary-email member)))

(defn unlocks-member? [key member]
  (core/unlocks? key :member (person/primary-email member)))

(defn is-member-key? "Checks if the supplied object can be considered a member key."
  [auth-key] (and (:token auth-key) (:member (:locks auth-key))))

(defn censor-member "Removes information that is unnecessary to reveal for clients."
  [member]
  (-> member
      (update-in [:auth-key] dissoc :locks)
      (update-in [:sign-key] dissoc :locks)))