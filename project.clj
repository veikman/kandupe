(defproject kandupe "0.1.0"
  :description "KANJIDIC analysis script for finding relations by meaning"
  :url "https://github.com/veikman/kandupe"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [hiccup "1.0.5"]]
  :main ^:skip-aot kandupe.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
