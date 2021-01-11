(defproject de.mzuther/moccafaux.core "1.4.1"
  :description "Adapt power management to changes in the environment."
  :url "https://github.com/mzuther/moccafaux"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/data.json "1.0.0"]
                 [org.clojure/tools.cli "1.0.194"]
                 [clj-systemtray "0.2.1"]
                 [com.rpl/specter "1.1.3"]
                 [jarohen/chime "0.3.2"]
                 [popen "0.3.1"]
                 [trptcolin/versioneer "0.2.0"]]

  :main ^:skip-aot de.mzuther.moccafaux.core
  :target-path "target/%s"
  :uberjar-name "moccafaux.jar"
  :resource-paths ["resources"]

  :profiles {:debug   {:debug       true
                       ;; :injections  [(newline)
                       ;;               (doseq [s (into {} (System/getProperties))]
                       ;;                 (prn s))
                       ;;               (newline)
                       ;;               (flush)]
                       :global-vars {*warn-on-reflection* true
                                     *assert*             true}}
             :uberjar {:aot         :all
                       :global-vars {*warn-on-reflection* true
                                     *assert*             false}}})
