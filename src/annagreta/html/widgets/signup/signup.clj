(ns annagreta.html.widgets.signup.signup)

(def signup-form
  [:form.signup-form.form-horizontal
   [:div.signup-status.alert {:style "display:none"}]
   [:fieldset
    [:div.control-group
     [:label.control-label {:for "email"} "Email"] [:div.controls [:input#email.email {:type "email" :placeholder "Email"}]]]
    [:div.control-group [:label.control-label {:for "password"} "Password"] [:div.controls [:input#password.password {:type "password" :placeholder "Password"}]]]]
   [:fieldset
    [:div.control-group [:div.controls [:button.signup-button.btn.btn-success {:type "button"} "Join!"]]]]])
