(ns annagreta.html.widgets.sign-in.cljs.sign-in "This sign-in code can be jacked into any page that has classes on elements according to the selectors in this file."
  (:require [jayq.core :as jq]
            [jayq.util :as jqu]
            [spexy.cljs.util :as spexy])
  (:use [cljs.reader :only [read-string]]))

(def $ jq/$)

(def annagreta-base-uri {:scheme "http" :hostname "localhost" :port 8060})

(defn submit-sign-in-form [success-redirect-uri]
  (spexy/rpost (assoc annagreta-base-uri
                 :params {:member #_(spexy/make-id-person (jq/attr ($ ".sign-in-form input.person") "value"))
                            (pr-str (jq/attr ($ ".sign-in-form input.person") "value"))
                          :password (pr-str (jq/attr ($ ".sign-in-form input.password") "value"))
                          :renew-member-token (pr-str true)})
               {:success (fn [data] (spexy/goto
                                     (spexy/make-uri-str (assoc success-redirect-uri :params {:member (pr-str (jq/attr ($ ".sign-in-form input.person") "value"))
                                                                                              :auth-key (pr-str (-> data :data :result :renew-member-token))}))))}))

(defn sign-out [success-redirect-uri]
  (spexy/rpost (assoc annagreta-base-uri
                 :params {:member (pr-str (-> (spexy/parse-window-location) :params :member))
                          :auth-key (pr-str (-> (spexy/parse-window-location) :params :auth-key))
                          :retract-member-key (pr-str true)})
               {:success (fn [data] (spexy/goto (spexy/make-uri-str success-redirect-uri)))}))

(defn ^:export init [page-parts-str]
  (let [{:keys [sign-in-success-redirect-uri]} (read-string page-parts-str)]
    (spexy/bind-events {"click" (fn [event field-id] (submit-sign-in-form sign-in-success-redirect-uri))} ".sign-in-form .sign-in-button")
    (.keypress ($ ".sign-in-form input") (fn [event] (if (= (. event -keyCode) 13) (submit-sign-in-form sign-in-success-redirect-uri)))) ;;13 = enter
    (spexy/bind-events {"click" (fn [event field-id] (sign-out sign-in-success-redirect-uri))} ".sign-out-button")
    #_(jq/attr ($ ".navbar a,button") "tabindex" "-1"))) ;;Remove nav menu items and buttons from tab selection

