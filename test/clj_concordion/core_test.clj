(ns clj-concordion.core-test
  (:require
    [clojure.test :refer :all]
    [io.aviso.repl :refer [install-pretty-exceptions]])
  (:import
    org.concordion.api.Fixture
    [org.concordion.internal ClassNameBasedSpecificationLocator
                             FixtureInstance
                             FixtureRunner]))


(install-pretty-exceptions)

(gen-class
  :name "math.AdditionFixture"
  :methods [[add [Integer Integer] Integer]
            [subtract [Integer Integer] Integer]])

(defn -add
  "Called by Concordion"
  [_ n1 n2]
  (int (+ n1 n2)))

(defn -subtract
  "Called by Concordion"
  [_ n1 n2]
  (int (- n1 n2)))

(defn run-fixture [fixture]
  (let [fixture-meta (doto (FixtureInstance. fixture)
                       (.beforeSpecification)
                       (.setupForRun fixture))]
    (.run
      (FixtureRunner.
        fixture-meta
        (ClassNameBasedSpecificationLocator.))
      fixture-meta)
    (.afterSpecification fixture-meta)))


(deftest concordion
  (run-fixture (math.AdditionFixture.)))
