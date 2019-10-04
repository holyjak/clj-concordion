(ns math.algebra-test
  (:require [clojure.test :refer :all]
            [clj-concordion.core :as cc])
  (:import (org.concordion.api.option MarkdownExtensions)))

; (io.aviso.repl/install-pretty-exceptions)

(defn parse-int [s]
  (Integer/parseInt s))

(defn add
  "Called by Concordion"
  [n1 n2]
  (int (+ (parse-int n1) (parse-int n2))))

(defn addStr [s1 s2]
  (str s1 s2))

(cc/deffixture Addition
  {:concordion/fail-fast                      true
   :concordion/fail-fast-exceptions           #{IndexOutOfBoundsException}
   :concordion/impl-status                    :expected-to-pass
   :concordion.option/markdown-extensions     [MarkdownExtensions/FENCED_CODE_BLOCKS]
   :concordion.option/copy-source-html-to-dir "/tmp/conc-copy"
   :concordion.option/declare-namespaces      ["ext" "urn:concordion-extensions:2010", "foo" "http://foo"]
   :cc/before-suite                           #(println "AdditionFixture: I run before each Suite")
   :cc/before-spec                            #(println "AdditionFixture: I run before e ach Spec")
   ;:cc/before-example                         #(throw (RuntimeException. (str "fake exception in " %)))
   :cc/before-example                         #(println "AdditionFixture: I run before each example" %)
   :cc/after-example                          #(println "AdditionFixture: I run after each example" %)
   ;;:cc/after-example                          #(throw (RuntimeException. (str "fake exception " %)))
   :cc/after-spec                             #(println "AdditionFixture: I run after each Spec")
   :cc/after-suite                            #(println "AdditionFixture: I run after each Suite")})

(defn reset-concordion
  "Reset Concordion's caches so that we can run repeatedly from REPL.
   Only needs to be called once per `(run-tests)` call."
  [f]
  (cc/reset-concordion!)
  (f))

(use-fixtures :once reset-concordion)