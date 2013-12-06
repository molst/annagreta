(ns annagreta.html.widgets.members-table.members-table
  (:require [jayq.core :as jq])
  (:use [jayq.util :only [log]]
        [cljs.reader :only [read-string]]))

(def $ jq/$)

(defn bad-request [jq-xhr text-status error-thrown]
  (log "400 bad request received, validation error?"))

(defn sign-in-error [jq-xhr text-status error-thrown]
  (log "sign in error: " text-status " --- " error-thrown))

(defn sign-in-success [data text-status jq-xhr]
  (set! (. js/window -location) (str (:next-location (read-string (. jq-xhr -responseText))))))

(defn submit-sign-in-form [member password-token]
  (jq/ajax "/sign-in"
           {:success sign-in-success
            :error sign-in-error
            :statusCode {:400 bad-request}
            :type "POST"
            :data {:member member
                   :password-token password-token}}))

(jq/bind ($ ".members-table .quick-sign-in-button") :click (fn [event]
                                                             (let [target-form (jq/parent ($ (. event -target)))
                                                                   person (jq/attr (jq/find target-form :input.person) "value")
                                                                   password-token (jq/attr (jq/find target-form :input.password-token) "value")]
                                                               (submit-sign-in-form person password-token))))