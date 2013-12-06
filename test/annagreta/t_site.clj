(ns annagreta.t-site
  (:use midje.sweet)
  (:require [hazel.state :as db])
  (:require [annagreta.site :as site])
  (:require [annagreta.treq :as annatreq])
  (:require [treq.core :as treq])
  (:require [ring.util.codec :as rinc])
  (:require [rocky.core :as rocky])
  (:require [shaky.core :as shaky])
  (:require [shaky.http :as http])
  (:require [torpo.core :as torpo])
  (:require [torpo.uri :as uri])
  (:require [torpo.hash :as hash])
  (:require [annagreta.member :as member]))

(def request      (partial rocky/request      site/routes))
(def request-body (partial rocky/request-body site/routes))
(def read-body    (partial rocky/read-body    site/routes))

(def post-data                {:set-member {:person/primary-email "k@anka.se" :person/nickname "kalle" :password "kpass"}})
(def post-data-with-sha-pw    (assoc post-data :member
                                     (-> (:member post-data)
                                         (assoc :password-token (hash/sha1-str (:password (:member post-data))))
                                         (dissoc :password))))
(def post-data-with-another-pw (update-in post-data [:member :password] (fn [x] (str "anotherpassword"))))
(def post-data-with-new-pw (assoc-in post-data [:member :new-password] "newpass"))
(def post-data-admin          {:member {:person/primary-email "adde@admin.se" :password "addepass" :locks {:tags ["admin" "surfer"]}}})

(def base-request {:scheme "http" :hostname "localhost" :port 8060})

(defn init []
  (let [censored-key (:renew-member-key (:result (http/block-read! (uri/prepare-for-transmission (uri/merge base-request {:params {:renew-member-key {:locks (:locks site/admin)}
                                                                                                                                       :member (:person/primary-email site/admin)
                                                                                                                                       :password (:password site/admin)}})))))]
    (:result (http/block-read! (uri/prepare-for-transmission (uri/merge base-request
                                                                            {:params {:auth-key (:token censored-key)
                                                                                      :member (:person/primary-email site/admin)
                                                                                      :pick {:auth-key (:token censored-key)
                                                                                             :member (:person/primary-email site/admin)}}}))))))

(defn admin-authenticated-get-request  [] (uri/merge base-request {:params (update-in (:pick (init)) [:auth-key] :token)}))
(defn admin-authenticated-post-request [] (assoc (admin-authenticated-get-request) :request-method :post))




(defmacro with-member [& forms]
  `(let [~'add-member-response-body (read-body "/member" :request-method :post :params (assoc post-data :auth-key (:auth-key (:pick (init)))))
         ~'member-token (-> ~'add-member-response-body :auth-key :token)]
     (do ~@forms)))

(defn add-member! [member]
  (:token (:set-member (:result (read-body (uri/prepare-for-transmission (uri/merge (admin-authenticated-post-request) {:params {:set-member member}})))))))



(fact "add member and get member"
  (let [token (add-member! {:person/nickname "kalle" :person/primary-email "k@anka.se" :password "kpass"})]
    (read-body (uri/prepare-for-transmission (uri/merge base-request {:params {:auth-key token :member "k@anka.se" :pick {:member "k@anka.se"}}}))))
  => #(= "k@anka.se" (-> % :result :pick :member :person/primary-email)))

(fact "update member"
  (let [token (add-member! {:person/nickname "kalle" :person/primary-email "k@anka.se" :password "kpass"})]
    (read-body (uri/prepare-for-transmission (uri/merge base-request {:params {:auth-key token :member "k@anka.se" :update-member {:person/primary-email "k@anka.se" :person/nickname "olle"}}}))))
  => #(= "olle" (-> % :result :update-member :person/nickname)))

(fact "add member and get member using http"
  (let [new-person {:person/nickname "kaxxe" :person/primary-email "k@axxa.se" :password "kpass"}
        token (add-member! new-person)
        auth-req (uri/merge base-request {:params {:auth-key token :member (:person/primary-email new-person)}})]
    (treq/pick http/block-read! auth-req {:member (:person/primary-email new-person)}))
  => #(= "k@axxa.se" (-> % :member :person/primary-email)))

(fact "add member and get members"
  (let [token (add-member! {:person/nickname "kalle" :person/primary-email "k@anka.se"})]
    (read-body (uri/prepare-for-transmission (uri/merge (admin-authenticated-get-request) {:params {:member "k@anka.se" :members-with-auth-keys :all}}))))
  => #(> (count %) 1))

(def new-pw (atom {:password "newpass" :new-password "kpass"}))
(defn toggle-new-pw []
  (reset! new-pw (if (= (:password @new-pw) "kpass")
                   {:password "newpass" :new-password "kpass"}
                   {:password "kpass" :new-password "newpass"})))
(defn insert-new-pw [post-data] (update-in post-data [:member] #(merge % (toggle-new-pw))))
(fact "renew sign key returns a working key"
  (with-member
    (let [pw-sha (:token (:renew-sign-key (:result (read-body (uri/prepare-for-transmission (uri/merge base-request {:params {:renew-sign-key (:password site/admin)
                                                                                                                                  :member (:person/primary-email site/admin)
                                                                                                                                  :password (:password site/admin)}}))))))]
      (read-body (uri/prepare-for-transmission (uri/merge (admin-authenticated-get-request) {:params {:pick {:auth-key pw-sha}}})))))
  => #(= (-> % :result :pick :auth-key :locks :sign)
         (:person/primary-email site/admin)))

(fact "renew sign key fails in case the supplied password is wrong"
  (read-body (uri/prepare-for-transmission (uri/merge base-request {:params {:renew-sign-key (:password site/admin)
                                                                                 :member (:person/primary-email site/admin)
                                                                                 :password "wrong-pw"}})))
  => #(not (-> % :result :renew-sign-key :locks)))

(fact "renew member key returns correct key"
  (let [new-person {:person/primary-email "ron@ron.fi" :password "123"}
        agr (admin-authenticated-get-request)
        apr (assoc agr :request-method :post)
        _ (:set-member (:result (read-body (uri/prepare-for-transmission (uri/merge apr {:params {:set-member new-person}})))))
        auth-key-1 (:renew-member-key (:result (read-body (uri/prepare-for-transmission (uri/merge base-request {:params {:renew-member-key {:locks {:a "AAA"}}
                                                                                                                              :member new-person
                                                                                                                              :password "123"}})))))
        auth-key-2 (:auth-key (:pick (:result (read-body (uri/prepare-for-transmission (uri/merge agr {:params {:pick {:auth-key (:token auth-key-1)}}}))))))]
    auth-key-2) => #(and (> (count (-> % :token)) 20)
                         (= "ron@ron.fi" (-> % :locks :member))
                         (= "AAA" (-> % :locks :a))))

(fact "renew member key fails to authorize when password is wrong"
  (let [new-person {:person/primary-email "lalle@lulle.fi" :password "correct-pw"}]
    (read-body (uri/prepare-for-transmission (uri/merge base-request {:params {:renew-member-key {:locks {:c "CCC"}}
                                                                                   :member new-person
                                                                                   :password "wrong-pw"}}))))
  => #(not (:locks (:renew-member-key (:result %)))))

(fact "renew member token using member id and password"
  (let [new-person {:person/primary-email "kanojoj@lulle.fi" :password "correct-pw"}]
    (read-body (uri/prepare-for-transmission (uri/merge (admin-authenticated-post-request) {:params {:set-member new-person}})))
    (read-body (uri/prepare-for-transmission (uri/merge base-request {:params {:renew-member-token true
                                                                                   :member new-person
                                                                                   :password "correct-pw"}}))))
  => #(> (count (-> % :result :renew-member-token)) 20))

(fact "get member key"
    (let [new-person {:person/primary-email "barak@lulle.fi" :password "correct-pw"}
          auth-key (:set-member (:result (read-body (uri/prepare-for-transmission (uri/merge (admin-authenticated-post-request) {:params {:set-member new-person}})))))]
      (:auth-key (:pick (:result (read-body (uri/prepare-for-transmission (uri/merge (admin-authenticated-get-request) {:params {:pick {:auth-key (:token auth-key)}}})))))))    
   => member/is-member-key?)

(fact "retract member key makes the key inaccessible, token cannot be used anymore"
  (let [new-person {:person/primary-email "kalleksk@lulle.fi" :password "correct-pw"}
        auth-key (:set-member (:result (read-body (uri/prepare-for-transmission (uri/merge (admin-authenticated-post-request) {:params {:set-member new-person}})))))]
    (read-body (uri/prepare-for-transmission (uri/merge base-request {:params {:auth-key (:token auth-key)
                                                                                   :member new-person
                                                                                   :retract-member-key true}})))
    (:result (read-body (uri/prepare-for-transmission (uri/merge (admin-authenticated-get-request) {:params {:pick {:auth-key (:token auth-key)}}})))))
  => #(not (-> % :pick :auth-key :locks)))

(fact "request for a widget only"
  (read-body (uri/prepare-for-transmission (uri/merge (admin-authenticated-get-request) {:params {:pick {:members-table true}}})))
  => #(-> % :result :pick :members-table))

(fact "add member with an extra lock, 'lelle'"
    (let [new-person {:person/primary-email "gallalle@brulle.fi" :password "correct-pw" :locks {:tags "lelle"}}
          auth-key (:set-member (:result (read-body (uri/prepare-for-transmission (uri/merge (admin-authenticated-post-request) {:params {:set-member new-person}})))))]
      (read-body (uri/prepare-for-transmission (uri/merge (admin-authenticated-get-request) {:params {:pick {:auth-key (:token auth-key)}}}))))
    => #(let [tags (-> % :result :pick :auth-key :locks :tags)]
          (or (and (sequential? tags) (some #{"lelle"}))
              (= tags "lelle"))))

(fact "pick auth-key without admin privilege does not reveal locks"
    (let [new-person {:person/primary-email "stukke@brulle.fi" :password "correct-pw"}
          auth-key (:set-member (:result (read-body (uri/prepare-for-transmission (uri/merge (admin-authenticated-post-request) {:params {:set-member new-person}})))))]
      (read-body (uri/prepare-for-transmission (uri/merge base-request {:params {:auth-key (:token auth-key) :member (:person/primary-email new-person)
                                                                                     :pick {:auth-key (:token auth-key)}}}))))
    => #(not (-> % :result :pick :auth-key :locks)))


(fact "pick member, sign-key and auth-key all at once"
  (let [agr (admin-authenticated-get-request)
        base-req (annatreq/uri-pw-to-sign-token (uri/merge agr {:params {:AAAAAAAAA "AAAAAAAAA" :password (:password site/admin)}}))
        pick (select-keys (:params base-req) [:auth-key :member :sign-key])]
    (treq/pick http/block-read! base-req pick))
  => #(= (set (keys %)) #{:member :auth-key :sign-key}))
