(ns clj-concordion.core
  (:require
    [clj-concordion.internal.utils :refer :all]
    [clojure.string :as str]
    [clojure.test :as test])
  (:import
    [org.concordion.internal ClassNameBasedSpecificationLocator
                             FixtureInstance
                             FixtureRunner]))

(defn run-fixture
  "Test a Concordion specification using the given fixture object
  (which provides the functions used in the specification .md).
  The specification file is found on the classpath based on the name
  of the fixture's class.
  "
  [^Object fixture]
  (let [fixture-meta (doto (FixtureInstance. fixture)
                       (.beforeSpecification)
                       (.setupForRun fixture))]
    (.run
      (FixtureRunner.
        fixture-meta
        (ClassNameBasedSpecificationLocator.))
      fixture-meta)
    (.afterSpecification fixture-meta)))

(defn- starts-with? [prefix sym]
  (-> sym name (str/starts-with? prefix)))

(defn- deffixture*
  [name methods]
  {:pre [(or (symbol? name) (string? name))
         (sequential? methods)
         (every? var? methods)]}
  (let [class-name (clojure.core/name name)
        prefix "-"
        methods*  (->> methods
                       (map var->method-descr)
                       (mapv (juxt :namesym :args :ret)))
        ;; implementation functions for the methods:
        defns     (map
                    (partial ->defn prefix)
                    methods)]
    `(do
       ~@defns
       (gen-class
         :name ~class-name
         :methods ~methods*)
       (test/deftest ~(symbol "concordion")
         (run-fixture (new ~(symbol class-name)))))))

(defmacro deffixture
  "Create a fixture object for a Concordion specification, exposing the functions used in the spec.,
   and a clojure.test test to execute the specification.

   Params:
     - name - a package-prefixed name of the generated fixture class, optionally ending in Fixture (symbol or string)
              The name is also used to find the specification .md/.html file.
     - methods - a vector of 1+ functions that will be exposed as methods on the fixture object
               The function parameters and return value may be type-hinted as ^int or ^bool,
               the default being String (the only 3 types supported by Concordion).

   Example:
     Given the spec math/Addition.md with 'yields [4](- \"?=add(#n1, #n2)\")'
     Implement `(defn ^int add [^int n1, ^int n2] (+ n1 n2)` and expose it to the spec.:
     `(deffixture math.AdditionFixture [add])`"
  [name methods]
  (deffixture* name (map resolve methods)))