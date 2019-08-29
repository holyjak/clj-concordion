(ns clj-concordion.internal.deffixture
  (:require
    [clj-concordion.internal.run :refer :all]
    [clj-concordion.internal.utils :refer :all]
    [clj-concordion.internal.interop :refer :all]
    [clojure.string :as cs]
    [clojure.test :as test]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.string :as cs])
  (:import (org.concordion.api ResultSummary)))

;--------------------------------------------------------------- resetting

(def fixtures
  "INTERNAL
   Note: This is not reliable, as it seems state can be wiped out between
   test runs. But it is good enough for the purpose of resetting state that
   has not been wiped out (e.g. when running repeatedly from REPL)."
  (atom #{}))

;--------------------------------------------------------------- deffixture*

(defn assert-test-ns [ns]
  (assert
    (cs/ends-with? (name (ns-name ns)) "-test")
    "The namespace using `deffixture` must end in -test so that clojure.test will find and run the generated test."))

(defn def-fixture-var* [var-sym opts]
  (let [var-type-sym (symbol (str var-sym "_Fixture"))] ;; can't have var and deftype of the same name
    `(do
       (def ~var-sym ~opts)
       (alter-meta! (var ~var-sym) assoc :cc/class (deftype ~var-type-sym [])))))

(defmacro def-fixture-var
  "Utility for tests: def the fixture var, similarly as `deffixture` does."
  [var-sym opts]
  (def-fixture-var* var-sym opts))

(defn ^ResultSummary new-fixture-run
  "Part of `deffixture` that wraps the fixture var in the `org.concordion.api.Fixture`
  meta object and tests the specification associated with it, returning the test result."
  [fixture-var]
  (let [fixture (new-fixture fixture-var @fixture-var)]
    (do (swap! fixtures conj fixture)) ;; store for (reset-concordion!)
    (run-specification fixture true)))

(defn deffixture*
  [name opts]
  {:pre [(or (symbol? name) #_(string? name))]}
  (assert-test-ns *ns*)
  (let [var-sym name
        no-asserts? (:cc/no-asserts? opts)]
    `(do
       ~(def-fixture-var* var-sym opts)
       (test/deftest ~(symbol (str var-sym "-test"))
         (let [result# (new-fixture-run (var ~var-sym))]
           (when (and
                   (not ~no-asserts?)
                   (zero? (+ (.getSuccessCount result#)
                             (.getExceptionCount result#)
                             (.getFailureCount result#))))
             (println (str "Warning: The specification  with the fixture " (var ~var-sym)
                           " seems to have no asserts.")))
           (test/is (zero? (.getExceptionCount result#)))
           (test/is (zero? (.getFailureCount result#))))))))

