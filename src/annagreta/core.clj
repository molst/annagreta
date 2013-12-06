(ns annagreta.core "Core authentication functions with minimal dependencies."
  (:require [torpo.hash :as hash]))

(defn make-key [locks & token]
  {:locks locks :token (if token (first token) (hash/make-unique-token))})

(defn unlocks? "Checks if 'key' unlocks all 'lock-entries' (lock type/value pairs). 'key' is a map representing a key that typically has already been loaded from the database on the form {:locks {:lock-type lock-value(s)}} (with any number of lock-type lock-value(s) pairs). If the first item in 'lock-entries' is a map, this is treated as the locks map and the other values in 'lock-entries' are ignored. This means both these types of call are supported: (unlocks? key lock-map) and (unlocks? key :lock-type-1 \"lock-val-1\")."
  [{locks-from-key :locks} & lock-entries]
  (let [results (for [lock-entry (seq (if (map? (first lock-entries))
                                        (first lock-entries)
                                        (apply hash-map lock-entries)))
                      :let [value-from-key ((key lock-entry) locks-from-key)
                            value-from-key-list (if (seq? value-from-key) value-from-key (list value-from-key))]]
                  (if (some #{(val lock-entry)} value-from-key-list) true false))]
    (if (or (empty? results) (some false? results))
      false
      true)))


(defn censor-key "Removes information that is unnecessary to reveal for clients."
  [key] (dissoc key :locks))