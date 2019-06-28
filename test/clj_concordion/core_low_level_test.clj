(ns clj-concordion.core-low-level-test
 "Tests of internal impl details that might change for stuff that is already
  covered by high-level tests, only intended to pinpoint problems faster.

  If a test fails but high-level ones succeed than it is likely outdated and
  can be deleted."
 (:require
   [clj-concordion.core :as cc]
   [clj-concordion.internal.deffixture :refer [def-fixture-var new-fixture-run]]
   [clj-concordion.internal.interop :as cci]
   [clojure.test :refer :all]))

;; The "class" of a fixture must be unique b/c it is used e.g. for caching
(deftest unique-fixture-class
  (def-fixture-var TestOneA {})
  (def-fixture-var TestOneB {})
  (is (not=
        (-> (cci/new-fixture #'TestOneA {}) .getFixtureType .getFixtureClass)
        (-> (cci/new-fixture #'TestOneB {}) .getFixtureType .getFixtureClass))))

(use-fixtures :once cc/cljtest-reset-concordion)


