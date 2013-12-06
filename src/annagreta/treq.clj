(ns annagreta.treq
  (:refer-clojure :exclude [resolve])
  (:require hiccup.core)
  (:require [torpo.core :as torpo])
  (:require [torpo.uri :as uri])
  (:require [torpo.hash :as hash])
  (:require [shaky.core :as shaky])
  (:require [shaky.http :as http])
  (:require [treq.core :as treq])
  (:require [hazel.core :as hazel])
  (:require annagreta.db.core)
  (:require [annagreta.core :as core])
  (:require [annagreta.person :as person])
  (:require [annagreta.member :as member])
  (:require [annagreta.db.member :as dbmember])
  (:require [annagreta.html.widgets.members-table.members-table :as members-table]))



(defn unlocks? [m locks]
  (core/unlocks? (:auth-key m) locks))

(defn signs? [{:keys [sign-key member]}]
  (member/signs? sign-key member))

(defn uri-pw-to-sign-token "Takes a password at a default location in 'uri', makes it sha 1, and puts it in the default location for the resolving of a sign key."
   [uri] (if-let [pw (->> uri :params :password)]
           (assoc-in uri [:params :sign-key] (hash/sha1-str pw))
           uri))

(defn auth-req-to-uri "Uses the authentication parameters from 'ring-req' to make 'uri' an authenticated request uri."
  [ring-req]
  (-> {:params (shaky/take-ring-params ring-req :member :auth-key :password)}
      (uri-pw-to-sign-token)))

(defn pw-to-sign-token "Takes a password at a default location in source, makes it sha 1, and puts it in the default location for the resolving of a sign key."
  [resolution] (if-let [pw (:password (:source resolution))]
                 (assoc-in resolution [:source :sign-key] (hash/sha1-str pw))
                 resolution))

(defn rule-unlocks? "Checks if an auth key found at a default location in the map 'result' unlocks the lock identified by 'auth-rule'. 'auth-rule' is a vector containing just a lock type, just a lock type and a lock value, or a lock type, an operator and a lock value. If only a lock type is present, a lock value will be searched for at default locations in 'result'. If a lock type and a lock value is supplied a check will simply be made to see wether the found auth key unlocks the lock value. If a lock type and a lock value are supplied with an operation keyword interposed between them, the operation will be attempted for the lock type and value."
  [{:keys [source result]} auth-rule]
  (case (count auth-rule)
      1 (case (auth-rule 0)
          :member (unlocks? result {:member (:person/primary-email (:member result))})
          :sign   (signs?   result)
          false)
      2 (unlocks? result (apply hash-map auth-rule))
      3 (let [[lock-type op op-val] auth-rule]
          (case op
            :at (unlocks? result {lock-type (:person/primary-email (get-in result op-val))})
            false))
      :else :error-too-many-items-in-auth-rule))

(defn auth-resolve "Runs all 'resolvers' for which :auth-rule succeeds. 'auth-resolvers' are run first in order to prepare data needed to make authentication possible. Each resolver can have an :auth-rule, which should be a list of lists. Each list are checked using the function 'rule-unlocks?'. Example usage: [[[:members :member-data] [:tags \"admin\"]] [[:members :member-meta] [:tags \"admin\"] [:member :at [:member]]]] means the location [:members :member-data] will be resolved if :source contains an authentication key that unlocks the lock :tags with value \"admin\". The location [:members :member-meta] will be resolved if :source contains an authentication key that unlocks the lock :tags with value \"admin\" OR if it unlocks the lock :member at the location [:member] in :source. If only a keyword is present in an auth rule, it will be authenticated using data at a default location in :source."
  [resolution resolvers auth-resolvers]
  (let [auth-res (treq/resolve (pw-to-sign-token resolution) auth-resolvers)]
    (reduce (fn [resolution resolver]
              (if (or (nil? (:auth-rule resolver)) ;;Always resolve if there is no auth rule
                      (some (fn [rule] (rule-unlocks? auth-res rule)) (:auth-rule resolver)))
                (treq/resolve resolution [resolver])
                resolution))
            resolution
            resolvers)))




;; Default resolvers for basic funtionality.

(defn auth-resolvers [{:keys [db db-conn remote-access-uri]}]
  [{:locations [[:auth-key] [:sign-key]]
    :access-fns [(fn [_ sel] (annagreta.db.core/get-key db sel))
                 (fn [_ sel] (treq/pick http/block-read! remote-access-uri {:auth-key sel}))]}

   {:locations [[:member]]
    :access-fns [(fn [_ sel] (dbmember/get-member db sel))
                 (fn [_ sel] (treq/pick http/block-read! remote-access-uri {:member sel}))]}])

(defn resolvers [{:keys [db db-conn remote-access-uri]}]
  [{:locations [[:pick :auth-key] [:pick :sign-key]] :auth-rule [[:tags "annagreta-admin"]]
    :access-fns [(fn [_ sel] (annagreta.db.core/get-key db sel))
                 (fn [_ sel] (treq/pick http/block-read! remote-access-uri {:auth-key sel}))]}

   {:locations [[:pick :member]] :auth-rule [[:tags "annagreta-admin"] [:member]]
    :access-fns [(fn [_ sel] (dbmember/get-member db sel))
                 (fn [_ sel] (treq/pick http/block-read! remote-access-uri {:member sel}))]}

   {:locations [[:members-with-auth-keys]] :auth-rule [[:tags "annagreta-admin"]]
    :access-fns [(fn [_ sel] (apply vector (dbmember/get-members-with-auth-keys db (if (= :all sel) nil sel))))]}

   {:locations [[:set-member]] :auth-rule [[:tags "annagreta-admin"]]
    :access-fns [(fn [_ sel] (dbmember/add-member-and-sign-in! db-conn sel (hash/sha1-str (:password sel))))]}

   {:locations [[:update-member]] :auth-rule [[:member]]
    :access-fns [(fn [res sel]
                   (let [old-member (dbmember/get-member db (->> res :source :member)) ;;Default to update the requesting members personal data
                         old-member (first (hazel/season-dbid db [old-member]))
                         updated-member (merge old-member sel)]
                     @(dbmember/update-member! db-conn updated-member)
                     updated-member))]}

   {:locations [[:renew-member-key]] :auth-rule [[:sign]]
    :access-fns [(fn [res sel] (dbmember/renew-member-key db-conn (:member (:source res)) sel))]}

   {:locations [[:renew-sign-key]] :auth-rule [[:sign]]
    :access-fns [(fn [res sel] (dbmember/renew-sign-key db-conn (:member (:source res)) sel))]}

   {:locations [[:renew-member-token]] :auth-rule [[:sign]]
    :insert-fn torpo/merge-in :access-fns [(fn [res _] (dbmember/renew-member-token db-conn (:member (:source res))))]}

   {:locations [[:retract-member-key]]
    :access-fns [(fn [res _] (do (dbmember/retract-member-key db-conn (:member (:source res))) true))]}

   {:locations [[:pick :members-table]] :auth-rule [[:tags "annagreta-admin"]]
    :access-fns [(fn [_ _] (hiccup.core/html (members-table/make-members-table (dbmember/get-members-with-auth-keys db nil))))]}])
