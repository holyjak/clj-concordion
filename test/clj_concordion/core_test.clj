(ns clj-concordion.core-test
  (:require
    [clj-concordion.core :as cc]
    [clj-concordion.internal.deffixture :as ccd :refer [def-fixture-var new-fixture-run]]
    [clj-concordion.internal.interop :as cci]
    [clojure.test :refer :all]))

;; Upon 2nd run of the same fixture, return the previously cached result
(deftest run-fixture-max-once
  (let [invocation-cnt (atom 0)]
    (def-fixture-var DummyFixtureA {:cc/before-spec #(swap! invocation-cnt inc)})
    (do
      (new-fixture-run #'DummyFixtureA)
      (new-fixture-run #'DummyFixtureA))
    (is (= 1 @invocation-cnt))))

;; Ensure spec is really run again after a concordion reset
(deftest reset-concordion
  (let [invocation-cnt (atom 0)]
    (def-fixture-var DummyFixtureB {:cc/before-spec #(swap! invocation-cnt inc)})
    (do
      (new-fixture-run #'DummyFixtureB)
      (cc/reset-concordion!)
      (new-fixture-run #'DummyFixtureB))
    (is (= 2 @invocation-cnt))))


(use-fixtures :once cc/cljtest-reset-concordion)

(comment
  (run-tests)
  (require '[clojure.pprint :as pp])

  (clojure.pprint/pprint (macroexpand-1
                           '(cc/deffixture Addition))))
