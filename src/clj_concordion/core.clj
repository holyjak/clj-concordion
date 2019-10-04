(ns clj-concordion.core
  (:require
    [clj-concordion.internal.run :as run]
    [clj-concordion.internal.deffixture :refer [deffixture* fixtures]]
    [clj-concordion.internal.utils :refer :all]
    [clj-concordion.internal.interop :refer :all]
    [clojure.spec.alpha :as s]
    [clojure.test :as test])
  (:import
    (org.concordion.api Fixture)))


#_(defn run-fixture
    "Test a Concordion specification using the given fixture object
  (which provides the functions used in the specification .md).
  The specification file is found on the classpath based on the name
  of the fixture's class.
  "
    [^Fixture fixture suite?]
    (let [fixture-meta (doto fixture
                         (before-suite suite?)
                         (.beforeSpecification)
                         (.setupForRun fixture))
          result (.run
                   (FixtureRunner.
                     fixture-meta
                     (ClassNameBasedSpecificationLocator.))
                   fixture-meta)]
      (do
        (.afterSpecification fixture-meta)
        (when suite?
          (.afterSuite fixture-meta)))
      result))

;;---------------------------------------------------------------------- resetting, test support

(defn reset-concordion!
  "Reset the results cache so that all tests will run anew instead of returning
  old, cached results. Intended for use from a REPL."
  []
  (run!
    (fn [^Fixture fixture]
      (.removeAllFromCache run/runResultsCache (.getFixtureType fixture)))
    @fixtures))

(defn cljtest-reset-concordion
  "clojure.test fixture to reset concordion between runs, see `reset-concordion!`
  Usage: `(clojure.test/use-fixtures :once cljtest-reset-concordion)`"
  [f]
  (reset-concordion!)
  (f))

(defmacro test-fixture
  "Test the specification associated with the fixture
  (by invoking the underlying clojure.test test function).
  Intended for use from a REPL.

  Useful in combination with the `:concordion/fail-fast` option when you want to
  run only the one problematic specification instead of all the specs [in the ns].

  Ex.:
   `(deffixture Xyz {:concordion/fail-fast true}) (test-fixture Xyz)`

   (Note: It also does `reset-concordion!` since even if added as a hook, it
   wouldn't be invoked.)"
  [fname]
  (reset-concordion!)
  (test/test-var
    (resolve (symbol (str (name fname) "-test")))))

(s/fdef test-fixture
  :args (s/cat :fixture-name symbol?))

;;---------------------------------------------------------------------- the deffixture macro & friends

(defmacro deffixture
  "Create a fixture object for a Concordion specification, exposing the functions needed by it,
   and a clojure.test test to execute the specification.

   Params:
     - name - a package-prefixed name of the generated fixture class, optionally ending in Fixture (symbol or string)
              The name is also used to find the specification .md/.html file, just as in
              a Java/JUnit-based Concordion instrumentation.
     - methods - a vector of 1+ functions that will be exposed as methods on the fixture object.
               The function parameters and return value may be type-hinted as `^int` or `^bool`,
               the default being `^String` (the only 3 types supported by Concordion).
     - opts   - options for Concordion and this. See the spec for `:cc/opts` and the README for explanation

   Example:
     Given the spec math/Addition.md with `yields [4](- \"?=add(#n1, #n2)\")`
     write `(defn ^int add [^int n1, ^int n2] (+ n1 n2)` and expose it to the spec. with:
     `(deffixture math.AdditionFixture [add])`.

   See [concordion instrumenting](https://concordion.org/instrumenting/java/markdown/) and
   [coding docs](https://concordion.org/coding/java/markdown/) for more details."
  [name & more]
  (let [[opts] more
        opts2check (eval opts)]
    (when opts2check
      ;; Fail fast to give err message early to the user
      (s/assert :cc/opts opts2check))
    (deffixture*
      name
      opts)))

(s/fdef deffixture
        :args (s/cat :name :cc/classname
                     :opts (s/? map?)))



