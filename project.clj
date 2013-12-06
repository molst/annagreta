(defproject annagreta "0.1"
  :description "annagreta is an authorization library designed to integrate with Clojure applications."
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}
  :url "https://github.com/molst/annagreta"
  :scm {:name "git" :url "https://github.com/molst/annagreta"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.datomic/datomic-free "0.9.4324"]
                 [ring/ring-core "1.2.1"]
                 [ring/ring-devel "1.2.1"]
                 [ring/ring-jetty-adapter "1.2.1"]
                 [ring-cors "0.1.0"]
                 [net.cgrand/moustache "1.1.0"]
                 [hazel "0.1"]
                 [torpo "0.1"]
                 [rocky "0.1"]
                 [shaky "0.1"]
                 [treq "0.1"]
                 [pisto "0.1"]]
  :profiles {:dev {:dependencies [[midje "1.5.1"]]}})