annagreta is a Clojure authorization library.

## Features

 * integrates well with Clojure programs
 * decomposed and non-intrusive - imposes minimal design on the client
 * token based - usernames and passwords need not be passed around
 * prepared to be used either as a REST service or a library
 * a client helper namespace (annagreta.treq) that minimizes the coding involved in using the REST API
 * has helpers for keeping member/user info in the authentication database, if desired
 * all features that come as a side effect of using Datomic, such as great possibilities to analyze the history of the authentication database
 * as use cases mature and the library is used, HTML widgets such as member lists and sign in / sign out forms are being made available via the REST API

## Annagreta does NOT

 * try conform to any authentication standards (in favor of simplicity)
 * (yet) address any channel security

## Getting Started

The core concept in annagreta is the notion of an 'auth-key' that can look like this:
```clj
{:token "abcd" :locks {:member "nina@stocktown.se"
                       :watch-user ["mike@gothcity.com" "greta@carnaby.uk"]
                       :pages ["^http://www.fish.com/.*"] :widgets ["stocks" "weather"]}}
```
This key is idenfitied by a token and can unlock a bunch of locks. The keys in 'locks' are totally arbitrary and depends on the design of the system using annagreta. A value like this can be loaded from annagreta and passed around to unlock functionality in a program. By being a value, it can also easily be passed around between different systems.

The following sections is about explaining how to resolve a request like this:
```
http://localhost/hello-world-resource?member="anna@stocktown.se"&auth-key="abcd"
```
It could show the client a user specific page if there is a key in the annagreta database that is identified by token "abcd" and has a lock for key :member with a value of "anna@stocktown.se", and another if no such key is found.

The unlocking of a feature is as simple as this:
```clj
(if (anna/unlocks? auth-key :widget "weather")
  (current-weather-html)
  (santa-claus))
```

There are also helper functions to make dealing with the typical use case of keys unlocking private functionality for a member in a system:
```clj
(if (member/unlocks-member? auth-key {:person/primary-email "nina@stocktown.se"})
  (current-weather-html)
  (santa-claus))
```

A more complete example of granting a user a page view:
```clj
(ns wildrank.site
  (:require [net.cgrand.moustache :as moustache])
  (:require [torpo.uri :as uri])
  (:require [treq.core :as treq])
  (:require [annagreta.treq :as annatreq])
  (:require [annagreta.member :as member]))

(defn annapick "Pick stuff according to the supplied (optional) map from annagreta. Always picks :member :auth-key identified by the corresponding request parameters from annagreta."
  [req & [pick-map]]
  (let [auth-uri (uri/merge annagreta-base-uri (annatreq/auth-req-to-uri req))]
    (treq/pick http/block-read! auth-uri ;as the request parameters are a natural flat map, or the top of a tree, treq is a good fit for resolving the params
               (merge (select-keys (:params auth-uri) [:member :auth-key])
                      pick-map))))

;This handler is taking care of requests like "http://localhost/hello-world-resource?member="anna@stocktown.se"&auth-key="abcd"
(defn hello-world-route-handler [req]
  (let [{:keys [member auth-key]} (annapick req)] ;picks member and auth-key from annagreta
    (if (member/unlocks-member? auth-key member)
      {:body "hello world GRANTED!!!"}
      {:body "go home"}))

(def routes (-> (moustache/app ["hello-world-resource"] (moustache/app :get hello-world-route-handler))
```

It is just as easy to grant functionality at a lower program level:

```clj
(ns webstuff
  (:require [annagreta.core :as anna])
  (:require [annagreta.person :as person])
  (:require [annagreta.member :as member]))

(defn some-html-div [{:keys [member auth-key]}]
  (let [member-map (person/make-id-person member)
        member-id (person/get-id member-map)]
    [:div
      (if (member/unlocks-member? auth-key member-map)
        [:ul.nav
         [:li [:a {:href (str "http://www.secrets.se/" (:person/nickname member))} "Your personal link"]]
        [:ul.nav
         [:li [:a {:href "http://www.public.se"} "Non-personal link"]]]))
      (when (anna/unlocks? auth-key :widget "weather")
        [:div (weather-widget)])]))
```
Notice that the function generates HTML data. Even if the HTML is hosted on Clojure in this case, rather than on something XMLish, the semantics are just the same and should be familiar to any web developer.


## Project Maturity

NOT very well tested. Might contain severe bugs. Have never been used on a production web site. Developed primarily for my personal use. Anything can change without notice.

## Artifacts

[on Clojars](https://clojars.org/annagreta). If you are using Maven, add the following repository
definition to your `pom.xml`:

```xml
<repository>
  <id>clojars.org</id>
  <url>http://clojars.org/repo</url>
</repository>
```

With Leiningen:
```
[annagreta "0.1"]
```

## Major dependencies

 * [Clojure](http://clojure.org/) (version 1.5.1)
 * [Datomic](http://docs.datomic.com/)

## License

Copyright (C) 2013 [Marcus Holst](https://twitter.com/zolst)

Licensed under the [Eclipse Public License v1.0](http://www.eclipse.org/legal/epl-v10.html) (the same as Clojure).