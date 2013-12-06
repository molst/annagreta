annagreta is an authorization library designed to integrate both easily and non-intrusively with any kind of Clojure program.

## Key features

 * integrates well with Clojure programs
 * decomposed and non-intrusive - imposes minimal design on the client
 * token based - usernames and passwords need not be passed around
 * stateless - by default, tokens are sent with each request so that multiple accounts can be accessed from the same browser
 * prepared to be either its own service or just a library used directly by any program
 * a client helper namespace (annagreta.treq) that minimizes the coding involved in using the REST API
 * can keep member/user info in the authentication database, if desired
 * all features that come as a side effect of using Datomic, such as great possibilities to analyze the history of the authentication database
 * as use cases mature and the library is used, HTML widgets such as member lists and sign in / sign out forms are being made available via the REST API

## Annagreta does NOT

 * try conform to any authentication standards
 * (yet) address any channel security

## Getting Started

The typical use case of granting a member (identified by some id) requesting something from a server might look like this on the server:
```clj
(:require [torpo.uri :as uri])
(:require [treq.core :as treq])
(:require [annagreta.treq :as annatreq])

(defn annapick "Pick stuff according to the supplied (optional) map from annagreta. Always picks :member :auth-key identified by the corresponding request parameters from annagreta."
  [req & [pick-map]]
  (let [auth-uri (uri/merge annagreta-base-uri (annatreq/auth-req-to-uri req))]
    (treq/pick http/block-read! auth-uri
               (merge (select-keys (:params auth-uri) [:member :auth-key])
                      pick-map))))

(defn hello-world-route-handler [req]
  (let [[member auth-key] (annapick req)] ;request params "member" and "auth-key" must be set to id's identifying a member and auth-key respectively
    (if (unlocks-member? auth-key member)
      {:body "hello world GRANTED!!!"}
      {:body "go home"}))

(def routes (-> (moustache/app ["hello-world-resource"] (moustache/app :get hello-world-route-handler))
```

The returned result from annapick looks something like this:
```clj
{:member   {:person/primary-email "nina@stocktown.se"}
 :auth-key {:token "abcd" :locks {:member "nina@stocktown.se"}}}
```

Note, however, that the core functionality of this library does not deal with members at all. An auth-key could be used to unlock any functionality in the server. This is another example:
```clj
{:token "1234"
 :locks {:page "http://some.url.se"
         :widget ["stocks" "weather"]}}
```

The above could be used like:
```clj
(if (unlocks? auth-key :widget "weather")
  {:body (current-weather-html)}
  {:body (santa-claus)})
```

## Project Maturity

NOT secure or well tested. Might contain severe bugs. Have never been used on a production web site yet. Use on your own risk! Developed primarily for my personal use. Anything can change without notice.

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