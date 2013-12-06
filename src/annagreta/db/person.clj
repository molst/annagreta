(ns annagreta.db.person "Core person metadata and database-related functions. A person is just a representation of any person."
  (:require [hazel.core :as db-util])
  (:require [hazel.entity :as entity])
  (:require [datomic.api :as d])
  (:require [torpo.core :as util])
  (:require [annagreta.person :as person]))

(def schema
  [{:db/id #db/id[:db.part/db]
    :db/ident :person/nickname
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity
    :db/index true
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :person/primary-email
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity
    :db/index true
    :db/doc "The most important email of a person, intended to be indexed for fast identification."
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :person/secondary-emails
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/many
    :db/doc "Email addresses of secondary importance."
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :person/links
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/many
    :db/doc "Urls to personal pages such as hompage, twitter, facebook etc."
    :db.install/_attribute :db.part/db}   
   {:db/id #db/id[:db.part/db]
    :db/ident :person/first-name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :person/last-name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   db-util/tags-schema])

(defn init! [db-conn] (d/transact db-conn schema))

(defn make-add-person-transaction [person]
  [(merge {:db/id #db/id[:db.part/user]} (entity/db-keys schema person))])

(defn add-person!
  "Takes a person on the form {:person/primary-email \"my-email\"} and a plain text password and adds it to the database."
  [db-conn person]
  (d/transact db-conn (make-add-person-transaction person)))

(defn season-id "Takes a map containing any of :db/id, :person/primary-email and :person/nickname, and seasons it with all three from the database."
  [db map]
  (db-util/season db (entity/db-keys schema map)
                  [:db/id :person/primary-email :person/nickname]
                   :person/primary-email :person/nickname))

(defn extort-id-person "Takes any person identifier (such as db id, primary email, nickname or person-map) and makes sure it contains all core identifying fields using 'db' if necessary. Currently, the only core identifying field (indended for use as a globally unique id across any system) is primary email."
  [db member]
  (let [id-person (person/make-id-person member)]
    (if-let [primary-email (person/primary-email id-person)]
      id-person
      (season-id db id-person))))

(defn extort-primary-email [db member]
  (person/primary-email (extort-id-person db member)))

(defn season-person "Takes a map containing any of :db/id, :person/primary-email and :person/nickname, and seasons it with the rest from the database."
  [db map]
  (db-util/season db (entity/db-keys schema map) (map :db/ident schema) :person/primary-email :person/nickname))

(defn get-person "Takes a person id value, which can be either an email, a nickname or an entity id, and returns the corresponding person-entity."
  [db id-value]
  (season-person db (person/make-id-person id-value)))

(defn get-persons
  "'match-val' can be a keyword, map or nil. The more info supplied, the more the output is filtered. nil will return all persons."
  [db match-val]
  (apply (partial db-util/season-entids
                  db
                  (db-util/find db (if (nil? match-val)
                                     :person/primary-email
                                     (if (map? match-val)
                                       (entity/db-keys schema match-val) match-val))))
         (map :db/ident schema)))

