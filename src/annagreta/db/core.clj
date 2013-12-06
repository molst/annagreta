(ns annagreta.db.core "Core authentication metadata and database-related functions."
  (:require [torpo.core :as torpo])
  (:require [torpo.hash :as hash])
  (:require [hazel.core :as db-util])
  (:require [datomic.api :as d])
  (:require [annagreta.core :as core]))

(def schema
  [{:db/id #db/id[:db.part/db]
    :db/ident :auth.key/token
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/index true
    :db/doc "A string value that enables the unlocking behavior of a key. Can be an automatically generated 'token' or a password created by user. This is NOT unique, which means many equal tokens, representing for example the same password, could co-exist silently. When retrieving a key based on a token, the resulting locks will be merged, which means that a retrived key for a token can get more access than intended. Token clashes are very unlikely for randomly generated tokens, but must be taken seriously when tokens represent passwords. In order to minimize the risk of one token owner to get access to the same things that the owner of another equal token, tokens representing passwords should always be replaced by a randomly generated token immediately after the password authorization is done. Also, tokens representing passwords should not give access to anyhting more than a new randomly generated token. This is token replacement is assured by the create-new-token-with-same-access function."
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :auth.key/lock
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/doc "A collection of locks that a key can unlock."
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :auth.key.lock/type
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one
    :db/doc "A type of lock that this key can unlock."
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :auth.key.lock/value
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/many
    :db/doc "Values related to a lock type one of which must be supplied for a key to be able to unlock."
    :db.install/_attribute :db.part/db}])

(defn init! [db-conn] (d/transact db-conn schema))

(defn make-add-key-transaction "Takes a map of locks on the form {:a-lock-type-as-keyword \"a-lock-value-as-string-OR-a-list-of-strings\"} and an optional token, and makes an add transaction of it. If no optional token is supplied, a random token is generated."
  [locks & more]
  (concat
   [{:db/id #db/id[:db.part/user -1]
     :auth.key/token (if-let [token (first more)] token (hash/make-unique-token))}]
   (apply concat (for [lock-type (keys locks) :let [lock-value (lock-type locks)] :when lock-value]
                   (if (sequential? lock-value)
                     (for [val lock-value]
                       {:db/id (d/tempid :db.part/user)
                        :auth.key/_lock #db/id[:db.part/user -1]
                        :auth.key.lock/type lock-type
                        :auth.key.lock/value val})
                     [{:db/id (d/tempid :db.part/user)
                       :auth.key/_lock #db/id[:db.part/user -1]
                       :auth.key.lock/type lock-type
                       :auth.key.lock/value lock-value}])))))

(defn get-locks-by-token "Retreives all locks that 'token' unlocks."
  [db token]
  (d/q `[:find ?lock-type ?lock-value :where
         [?key :auth.key/token ~token]
         [?key :auth.key/lock ?lock]
         [?lock :auth.key.lock/type ?lock-type]
         [?lock :auth.key.lock/value ?lock-value]] db))

(defn get-matching-locks "Gets all locks that matches the arguments."
  [db token lock-type lock-value]
  (filter #(and (= lock-type (first %)) (= lock-value (second %))) (seq (get-locks-by-token db token))))

(defn get-key
  "Retrives a key on the form {:token \"token-string\" :date-of-expiry epoch-time-in-millis :locks {:lock-type \"lock-value-string-OR-list-of-strings\"} from the database. The key represent all the locks that all keys with that token unlocks. Note that if more than one token in the database are identical to 'token', this function will return a key with access to all the combined locks."
  [db token]
  (when token
    (let [locks-seq (seq (get-locks-by-token db token))
          locks-maps (for [lock-type-value-pair locks-seq]
                       {(first lock-type-value-pair) (second lock-type-value-pair)})
          locks (when (seq locks-maps) (apply torpo/merge-and-seq-distinctly locks-maps))]
      (core/make-key locks token))))

(defn get-tokens-by-lock
  "Returns a seq of tokens of keys that can unlock any lock matching 'lock-type' and 'lock-value'. Note that there may be duplicate tokens, and that is hidden by this query."
  [db lock-type lock-value]
  (map first (seq (d/q `[:find ?token :where
                         [?lock :auth.key.lock/type ~lock-type]
                         [?lock :auth.key.lock/value ~lock-value]
                         [?key :auth.key/lock ?lock]
                         [?key :auth.key/token ?token]] db))))

(defn get-keys-by-lock
  "Returns a seq of complete keys that matches lock-type and lock-value."
  [db lock-type lock-value]
  (for [token (get-tokens-by-lock db lock-type lock-value)]
    (get-key db token)))

(defn make-retract-matching-keys-transaction "Makes a transaction that retracts all keys that completely matches 'locks'."
  [db locks]
  (let [number-of-locks (keys locks)
        key-ids (for [lock-type number-of-locks :let [lock-value (lock-type locks)] :when lock-value]
                  (seq (d/q `[:find ?key :where
                              [?lock :auth.key.lock/type ~lock-type]
                              [?lock :auth.key.lock/value ~lock-value]
                              [?key :auth.key/lock ?lock]] db)))
        entities-to-retract (for [entity-hit-seq (partition-by identity (sort (flatten key-ids)))]
                              (if (= (count entity-hit-seq) (count number-of-locks))
                                (first entity-hit-seq)))]
    (for [entity-to-retract entities-to-retract :when entity-to-retract]
      [:db.fn/retractEntity entity-to-retract])))

(defn retract-matching-keys "Retracts all keys that completely matches 'locks'."
  [db-conn locks]
  (d/transact db-conn (make-retract-matching-keys-transaction (d/db db-conn) locks)))

(defn replace-matching-keys "Replaces all keys that completely matches :locks in 'match-key' with one corresponding with the :locks in new-key. :token or any other keys than :locks in 'match-key' are ignored. If no :token is supplied in 'new-key', a random token is generated for the new key."
  [db-conn match-key new-key]
  (let [token (if-let [new-token (:token new-key)] new-token (hash/make-unique-token))
        tx (concat
            (make-retract-matching-keys-transaction (d/db db-conn) (:locks match-key))
            (make-add-key-transaction (:locks new-key) token))
        trans @(d/transact db-conn tx)]
    (core/make-key (:locks new-key) token)))

(defn renew-token "Randomly generates a new token for all keys that match the locks in the supplied key. Should be used before sharing/publishing tokens that are likely to be duplicated in the database. CURRENTLY ONLY SUPPORTS ONE MATCH LOCK - THIS SHOULD BE FIXED BY SUPPORTING VARARGS"
  [db-conn match-key]
  (let [lock (first (seq (:locks match-key))) ;;CURRENTLY ONLY SUPPORTS ONE MATCH LOCK - THIS SHOULD BE FIXED BY SUPPORTING VARARGS
        complete-key (first (get-keys-by-lock (d/db db-conn) (key lock) (val lock)))]
    (when complete-key (:token (replace-matching-keys db-conn match-key (dissoc complete-key :token))))))