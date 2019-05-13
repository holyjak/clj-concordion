(ns clj-concordion.core-test
  (:require
    [clj-concordion.core :as cc]
    [clojure.test :refer :all]
    [io.aviso.repl :refer [install-pretty-exceptions]])
  (:import (org.concordion.api.option MarkdownExtensions)))

(install-pretty-exceptions)

(defn ^Integer multiply
  "Called by Concordion"
  [^Integer n1, ^Integer n2]
  (int (* n1 n2)))

(defn ^Integer add
  "Called by Concordion"
  [^Integer n1, ^Integer n2]
  (int (+ n1 n2)))

(defn ^Integer subtract
  "Called by Concordion"
  [^Integer n1, ^Integer n2]
  (int (- n1 n2)))

(cc/deffixture
  "math.AlgebraFixture"
  [add multiply subtract])


(cc/deffixture
  "math.algebra.AdditionFixture"
  [add]
  {:concordion/full-ognl                      false
   :concordion/fail-fast                      true
   :concordion/fail-fast-exceptions           [IndexOutOfBoundsException]
   :concordion/impl-status                    :expected-to-pass
   :concordion.option/markdown-extensions     [MarkdownExtensions/FENCED_CODE_BLOCKS]
   :concordion.option/copy-source-html-to-dir "/tmp/conc-copy"
   :concordion.option/declare-namespaces      []
   ::cc/before-suite                          #(println "AdditionFixture: I run before each Suite")
   ::cc/before-spec                           #(println "AdditionFixture: I run before each Spec")
   ;::cc/before-example                        #(println "AdditionFixture: I run before each example")
   ;::cc/after-example                         #(println "AdditionFixture: I run after each example")
   ::cc/after-spec                            #(println "AdditionFixture: I run after each Spec")
   ::cc/after-suite                           #(println "AdditionFixture: I run after each Suite")})

(comment
  (require '[clojure.pprint :as pp])
  (pp/pprint (macroexpand-1
               '(cc/deffixture
                  "math.algebra.AdditionFixture"
                  [add]))))
