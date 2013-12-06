(ns annagreta.db.member "A member is a person that has registered for authenticated use of the system by submitting a valid primary email address."
  (:require [datomic.api :as d])
  (:require [hazel.core :as hazel])
  (:require [torpo.core :as util])
  (:require [torpo.hash :as hash])
  (:require [annagreta.db.core :as dbcore])
  (:require [annagreta.db.person :as dbperson])
  (:require [annagreta.person :as person]))

(defn init! [db-conn] (dbperson/init! db-conn))

(defn make-add-member-transaction [person password]
  (concat
   (dbperson/make-add-person-transaction person)
   (dbcore/make-add-key-transaction {:sign (:person/primary-email person)} password)))

(defn add-member!
  "Takes a person on the form {:person/primary-email \"my-email\"} and a plain text password and adds it to the database. The connection to a password makes it a member."
  [db-conn person password] (d/transact db-conn (make-add-member-transaction person password)))

(defn update-member! [db-conn person] (d/transact db-conn [person]))

(defn retract-member-key [db-conn member]
  (dbcore/retract-matching-keys db-conn {:member (person/primary-email member)}))

(defn renew-member-key "Retrieves a fresh and unique member key that unlocks personal data. Additional locks this member key should be able to unlock may be supplied via a :locks entry in 'new-key'."
  [db-conn member new-key]
  (let [id-locks {:member (dbperson/extort-primary-email (d/db db-conn) member)}
        new-locks (merge id-locks (:locks new-key))]
    (dbcore/replace-matching-keys db-conn {:locks id-locks} {:locks new-locks})))

(defn renew-member-token "Retrieves a new member key identical to an already existing key, but with a new token."
  [db-conn member] (dbcore/renew-token db-conn {:locks {:member (person/primary-email member)}}))

(defn renew-sign-key "Retrieves a fresh and unique key that unlocks features that require signing. Intentionally cannot unlock anything more than the :sign lock type."
  [db-conn member password]
  (let [id-locks {:sign (dbperson/extort-primary-email (d/db db-conn) member)}]
    (dbcore/replace-matching-keys db-conn {:locks id-locks} {:token (hash/sha1-str password) :locks id-locks})))

(defn get-members [db {:keys [match-val]}] (dbperson/get-persons db match-val))

(defn get-members-with-auth-keys "Retrieves members together with related auth keys."
  [db access-info]
  (for [member (get-members db access-info) :let [member-email (:person/primary-email member)]]
    (-> member
        (assoc :auth-key (first (dbcore/get-keys-by-lock db :member member-email)))
        (assoc :sign-key (first (dbcore/get-keys-by-lock db :sign member-email))))))

(defn get-member "Convenience function for accessing one member via 'member', which is either a string id or a person map."
  [db member] (when (seq member) (first (get-members db {:match-val (person/make-id-person member)}))))

(defn get-member-with-auth-keys "Convenience function for accessing one member, including auth-keys, via 'member', which is either a string id or a person map."
  [db member] (first (get-members-with-auth-keys     db {:match-val (person/make-id-person member)})))

(defn add-member-and-sign-in! [db-conn member sha-password]
  (do @(add-member! db-conn member sha-password)
      (renew-member-key db-conn member (select-keys member [:locks]))))
