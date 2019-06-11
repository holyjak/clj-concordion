(ns clj-concordion.core-test
  (:require
    [clj-concordion.core :as cc]
    [clojure.test :refer :all]
    [io.aviso.repl]))

(io.aviso.repl/install-pretty-exceptions)


(comment
  (run-tests)
  (require '[clojure.pprint :as pp])
  (cc/deffixture2
    "math.algebra.AdditionFixture"
    [add]
    {:cc/before-suite                 (constantly nil)
     :concordion/fail-fast-exceptions [IndexOutOfBoundsException]})
  (pp/pprint (macroexpand-1
               '(cc/deffixture Addition))))
