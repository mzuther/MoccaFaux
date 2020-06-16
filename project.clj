(defproject moccafaux "0.1.0-SNAPSHOT"
  :description "Adapt power management to changes in the environment."
  :url "http://de.mzuther/moccafaux"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/data.json "1.0.0"]
                 [jarohen/chime "0.3.2"]]
  :main ^:skip-aot moccafaux.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
