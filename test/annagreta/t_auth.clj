(ns annagreta.t-auth
  (:use midje.sweet)
  (:require [annagreta.core :as core])
  (:require [annagreta.db.core :as dbcore])
  (:require [annagreta.db.member :as dbmember])
  (:require [annagreta.site :as site])
  (:require [hazel.state :as db])
  (:require [rocky.core :as rocky])
  (:require [torpo.core :as torpo])
  (:require [datomic.api :as d]))

(def kalle {:person/primary-email "kalle@kula.se"})

(fact "renew member key with additional tag locks"
  (let [token (-> (dbcore/replace-matching-keys (->> site/state :auth-db-conn) kalle (assoc kalle :locks {:member "kalle@kula.se" :tags ["admin" "kanel"]})) :token)]
    (dbcore/get-key (d/db (->> site/state :auth-db-conn)) token)) => (fn [result] (and (= (-> result :locks :member) "kalle@kula.se")
                                                              (some #{"admin"} (-> result :locks :tags))
                                                              (some #{"kanel"} (-> result :locks :tags)))))

(fact "renew token"
  (let [trans @(dbmember/add-member! (->> site/state :auth-db-conn) {:person/primary-email "lasse@osse.se"} "lassepass")
        member (dbmember/get-member (d/db (->> site/state :auth-db-conn)) "lasse@osse.se")
        old-key (dbmember/renew-member-key (->> site/state :auth-db-conn) member {:locks {:tags "rulle"}})
        new-key (dbcore/renew-token (->> site/state :auth-db-conn) old-key)]
    {:old-key old-key :new-key new-key}) => #(and (not= (:token (:old-key %)) (:new-key %))))

(fact "unlocks? works in simplest case - positive" (core/unlocks? {:locks {:member "nisse"}} :member "nisse") => true)
(fact "unlocks? works in simplest case - negative" (core/unlocks? {:locks {:member "nisse"}} :member "nisseZZZ") => false)
(fact "unlocks? multiple values - positive"        (core/unlocks? {:locks {:member "nisse" :vehicle "ford"}} :member "nisse" :vehicle "ford") => true)
(fact "unlocks? multiple values - negative -  all must be fulfilled" (core/unlocks? {:locks {:member "nisse" :vehicle "ford"}} :member "nisse" :vehicle "fordZZZ") => false)
