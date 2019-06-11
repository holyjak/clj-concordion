(ns math-test
  (:require [clojure.test :refer :all]
            [clj-concordion.core :as cc]))

; (io.aviso.repl/install-pretty-exceptions)

(defn parse-int [s]
  (Integer/parseInt s))

(defn multiply
  "Called by Concordion"
  [n1, n2]
  (int (* (parse-int n1) (parse-int n2))))

(defn subtract
  "Called by Concordion"
  [n1, n2]
  (int (- (parse-int n1) (parse-int n2))))

(cc/deffixture Algebra
               {:cc/before-suite   #(println "AlgebraFixture: I run before each Suite")
                :cc/before-spec    #(println "AlgebraFixture: I run before each Spec")
                :cc/before-example #(println "AlgebraFixture: I run before each example" %)
                :cc/after-example  #(println "AlgebraFixture: I run after each example" %)
                :cc/after-spec     #(println "AlgebraFixture: I run after each Spec")
                :cc/after-suite    #(println "AlgebraFixture: I run after each Suite")})

(defn reset-concordion
  "Reset Concordion's caches so that we can run repeatedly from REPL.
   Only needs to be called once per `(run-tests)` call."
  [f]
  (cc/reset-concordion!)
  (f))

(use-fixtures :once reset-concordion)