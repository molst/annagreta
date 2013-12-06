(ns annagreta.html.widgets.members-table.members-table
  (:require [annagreta.person :as person])
  (:require [torpo.core :as torpo]))

(defn make-members-table [members]
  [:table.members-table.table;;.table-condensed
   [:thead
    [:tr         
     [:th {:style "border-top: none"} (person/print-all-names {:person/first-name "Firstname" :person/last-name "Lastname" :person/nickname "Nickname"})]
     [:th {:style "border-top: none"} "Primary Email"]
     [:th {:style "border-top: none"} "Actions"]]]
   (for [member members]
    [:tr
     [:td (person/print-all-names member)]
     [:td (:person/primary-email member)]
     [:td {:style "padding:2px"}
      [:form.quick-sign-in-form {:style "margin:2px"}
       [:input.person {:type "hidden" :name "person" :value (-> member :sign-key :locks :sign)}]
       [:input.password-token {:type "hidden" :name "password-token" :value (-> member :sign-key :token)}]
       [:button.quick-sign-in-button.btn {:type "button" :style "margin-left: 0.5em"} "Sign in"]]]])])

