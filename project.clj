(defproject githack "0.1.0-SNAPSHOT"
  :description "GitHub + NetHack = zero-player RPG"
  :url "http://githack.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/java.jdbc "0.3.5"]
                 [ring "1.5.0"]
                 [hiccup "1.0.5"]
                 [clj-http "3.1.0"]
                 [org.xerial/sqlite-jdbc "3.7.2"]
                 [bothack "1.0.0-SNAPSHOT"]
                 [overtone/at-at "1.2.0"]
                 [tentacles "0.5.1"]]
  :main ^:skip-aot githack.core
  :target-path "target/%s"
  :plugins  [[lein-ring "0.10.0"]]
  :ring {:handler githack.core/app}
  :profiles {:uberjar {:aot :all}})
