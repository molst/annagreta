(ns annagreta.t-client
  (:use midje.sweet)
  (:require [annagreta.treq :as t]))

(fact "rule-unlocks? can check a member lock value located at a default location"
  (t/rule-unlocks? {:result {:auth-key {:locks {:member "nisse"}}
                             :member {:person/primary-email "nisse"}}}
                   [:member]) => true)

(fact "rule-unlocks? works with lock type and lock value supplied"
  (t/rule-unlocks? {:result {:auth-key {:locks {:member "nisse"}}
                             :member {:person/primary-email "nisse"}}}
                   [:member "nisse"]) => true)

(fact "rule-unlocks? with lock type, operator and lock value unlocks correctly"
  (t/rule-unlocks? {:result {:auth-key {:locks {:member "nisse"}}
                             :member {:person/primary-email "nisse"}}}
                   [:member :at [:member]]) => true)

(fact "rule-unlocks? with lock type, operator and lock value does NOT unlock if the operator is invalid"
  (t/rule-unlocks? {:result {:auth-key {:locks {:member "nisse"}}
                             :member {:person/primary-email "nisse"}}}
                   [:member :NO-OPERATION [:member]]) => false)
