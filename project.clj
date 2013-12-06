(defproject annagreta "0.2-SNAPSHOT"
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
                 [hazel "0.2-SNAPSHOT"]
                 [torpo "0.2-SNAPSHOT"]
                 [rocky "0.2-SNAPSHOT"]
                 [shaky "0.2-SNAPSHOT"]
                 [treq "0.2-SNAPSHOT"]
                 [pisto "0.2-SNAPSHOT"]]
  :profiles {:dev {:dependencies [[midje "1.5.1"]]}})