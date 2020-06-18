(defproject de.mzuther/moccafaux.core "1.0.0"
  :description "Adapt power management to changes in the environment."
  :url "http://code.mzuther.de/"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/data.json "1.0.0"]
                 [jarohen/chime "0.3.2"]
                 [popen "0.3.1"]
                 [trptcolin/versioneer "0.2.0"]]

  :main ^:skip-aot de.mzuther.moccafaux.core
  :target-path "target/%s"

  :profiles {:uberjar {:aot :all}})
