(defproject clj-concordion "0.1.1-SNAPSHOT"
  :description "clojure.test integration for https://concordion.org"
  :url "https://github.com/holyjak/clj-concordion"
  :license {:name "Unlicense"
            :url "https://choosealicense.com/licenses/unlicense/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 ;; Added here for IntelliJ's sake:
                 [org.concordion/concordion "2.2.0"]
                 [org.clojure/tools.logging "0.4.0"]]
  :aot [clj-concordion.internal.interop clj-concordion.internal.run] ;; -> Java classes for Concordion interop
  :plugins [[lein-auto "0.1.3"]]
  :repl-options {:init-ns clj-concordion.core}
  :profiles {:test {:dependencies [[io.aviso/pretty "0.1.37"]]}
             :debug {:jvm-opts ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"]}})
