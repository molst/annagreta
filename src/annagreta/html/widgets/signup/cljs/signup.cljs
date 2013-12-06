(ns annagreta.html.widgets.cljs.signup
  (:require [jayq.core :as jq]
            [spexy.cljs.util :as u])
  (:use [jayq.util :only [log]]
        [cljs.reader :only [read-string]]))

(def $ jq/$)

(defn bad-request [jq-xhr text-status error-thrown]
  (jq/remove-class ($ :.signup-status) "alert-success")
  (jq/add-class ($ :.signup-status) "alert-error")
  (let [validation-errors (:validation-errors (read-string (. jq-xhr -responseText)))]
    (jq/inner ($ :.signup-status) (str "Whoups, try again please...<br/>" (u/print-validation-errors validation-errors))))
  (jq/show ($ :.signup-status) 70)
  (log "400 bad request received, validation error?"))

(defn signup-error [jq-xhr text-status error-thrown]
  (log "signup error: " text-status " --- " error-thrown))

(defn signup-success [data text-status jq-xhr]
  (jq/remove-class ($ :.signup-status) "alert-error")
  (jq/add-class ($ :.signup-status) "alert-success")
  (jq/text ($ :.signup-status) "Thanks! A verification email has been sent to the email address you supplied. Please click the link in the email within a week in order to open your Coderank account. If you do not get an email within a couple of minutes, you may try again with either the same or another email address.")
  (jq/show ($ :.signup-status) 70)
  (.load (jq/parent ($ :.members-table)) "/members-table"))

(defn submit-signup-form []
  (jq/ajax "/member"
           {:success signup-success
            :error signup-error
            :statusCode {:400 bad-request}
            :type "POST"
            :data {:email (jq/attr ($ ".signup-form input.email") "value")
                   :password (jq/attr ($ ".signup-form input.password") "value")}}))

(jq/bind ($ ".signup-form .signup-button") :click #(submit-signup-form))

(.keypress ($ ".signup-form input") (fn [event] (if (= (. event -keyCode) 13) (submit-signup-form)))) ;;13 = enter
