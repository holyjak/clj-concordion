(defproject clj-concordion "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 ;; Added here for IntelliJ's sake:
                 [org.concordion/concordion "2.2.0"]]
  :plugins [[lein-auto "0.1.3"]]
  :repl-options {:init-ns clj-concordion.core}
  :profiles {:test {:aot [clj-concordion.core-test] ;; TODO RegExp for Conc.Spec namespaces
                    :dependencies [[org.concordion/concordion "2.2.0"]
                                   [io.aviso/pretty "0.1.37"]]}})
