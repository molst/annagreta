(ns annagreta.site
  (:use [net.cgrand.moustache :exclude [not-found]])
  (:use ring.middleware.params)
  (:use [ring.adapter.jetty :only [run-jetty]])
  (:use [ring.middleware.stacktrace :only [wrap-stacktrace wrap-stacktrace-web]])
  (:require [ring.util.codec :as rinc])
  (:require [ring.middleware.cors :as cors])
  (:require [datomic.api :as d])
  (:require [pisto.core :as pisto])
  (:require [hazel.state :as db])
  (:require [torpo.hash :as hash])
  (:require [treq.core :as treq])
  (:require [annagreta.treq :as t])
  (:require [annagreta.db.core :as dbcore])
  (:require [annagreta.db.member :as dbmember]))





(def admin {:person/primary-email "admin@annagreta.com"
            :person/nickname "aadmin"
            :password "aapass"
            :sign-key (hash/sha1-str "aapass")
            :locks {:tags "annagreta-admin"}})

(defonce state nil)




(defn treq-handler [req]
  {:body (str (let [ai {:db (d/db (:auth-db-conn state)) :db-conn (:auth-db-conn state)}
                    result (t/auth-resolve (treq/ring-request-to-resolution req)
                                           (t/resolvers ai)
                                           (t/auth-resolvers ai))]
                result))})

(def routes
  (-> (app

       [""] (app wrap-params :get treq-handler :post treq-handler))
   
   (cors/wrap-cors :access-control-allow-origin #"http://localhost:8080") ;;accept cross-domain requests from pages loaded from these origins
   (wrap-stacktrace)))





(defn connect-and-init! [db-uri]
  (d/create-database db-uri)
  (let [db-conn (d/connect db-uri)]
    (db/add-db-functions! db-conn)
    (dbcore/init! db-conn)
    (dbmember/init! db-conn)
    (dbmember/add-member-and-sign-in! db-conn admin (hash/sha1-str (:password admin)))
    db-conn))

(defmethod pisto/start-part :annagreta [[type {:keys [config]}]]
  (let [db-uri (:uri (:db config))
        site-jetty-port (:port (:uri config))]
    (alter-var-root #'state
                    (fn [_]
                      {:auth-db-conn (when db-uri (connect-and-init! db-uri))
                       :site-jetty (when site-jetty-port (run-jetty #'routes {:port site-jetty-port :join? false}))}))))

(defmethod pisto/stop-part  :annagreta [[type part]]
  (let [db-uri (:uri (:db (:config part)))
        site-jetty (:site-jetty (:state part))]
    (when site-jetty (.stop site-jetty))
    (d/delete-database db-uri)))
