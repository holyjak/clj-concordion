(ns clj-concordion.core
  (:require
    [clj-concordion.internal.utils :refer :all]
    [clj-concordion.internal.interop :refer :all]
    [clj-concordion.specs :as ccs]
    [clojure.test :as test]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.string :as cs])
  (:import
    (org.concordion Concordion)
    (org.concordion.api Fixture FixtureDeclarations ResultSummary Runner)
    (org.concordion.internal FixtureRunner
                             FixtureType
                             FailFastException RunOutput)
    (org.concordion.internal.cache RunResultsCache)))

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

(def runResultsCache RunResultsCache/SINGLETON)

;;---------------------------------------------------------------------- resetting

(def fixtures
  "INTERNAL
   Note: This is not reliable, as it seems state can be wiped out between
   test runs. But it is good enough for the purpose of resetting state that
   has not been wiped out (e.g. when running repeatedly from REPL)."
  (atom #{}))

(defn reset-concordion!
  "Reset the results cache so that all tests will run anew."
  []
  (run!
    (fn [^Fixture fixture]
      (.removeAllFromCache runResultsCache (.getFixtureType fixture)))
    @fixtures))

;;---------------------------------------------------------------------- running

(defn- base-example-name
  "Drop ? and all after that, if present"
  [example]
  (cs/replace example #"\?.*$" ""))

(defn- assert-unique-examples [^FixtureDeclarations ftype examples]
  (let [uniq (->> examples (map base-example-name) set)
        dupl (->> examples (remove uniq) set)]
    (when (seq dupl)
      (throw
        (ex-info "Specification contains non-unique example names"
                 {:repeated dupl :all examples
                  :fixture (.getName (.getFixtureClass ftype))})))))

(defn run-fixture-examples
  "Test a Concordion specification using the given fixture object
  (which provides the functions used by the specification .md file).
  The specification file is found on the classpath based on the name
  of the fixture's class.
  See [run-specification]
  "
  [^Fixture fixture ^FixtureRunner runner ^Concordion concordion]
  (let [ftype (.getFixtureType fixture)
        examples (.getExampleNames concordion ftype)]
    (assert-unique-examples ftype examples)
    (try
      (doall
        (map #(try
                (.beforeExample fixture %)
                (doto (.run runner % fixture)
                  (.assertIsSatisfied ftype))
                (catch Throwable e
                  ;; Ignore all non FailFastExc; they are already
                  ;; recorded in the results summary
                  (when (instance? FailFastException e)
                    (throw e)))
                (finally
                  (.afterExample fixture %)))
              examples)))))

(defn- ^ResultSummary cached-spec-result
  "Return a previously cached result of the spec represented by this `fixture` (or nil).

  The cache has 1 result / example and also a composed result
  for the whole spec (under the 'example' with nil name).
  Its goal is to ensure that each fixture is run at most once. This increases the speed of tests
  when the same fixture is being run by multiple concordion:run commands - as well as by a top-level test itself.
  "
  [^Fixture fixture]
  (some->
    ^RunOutput
    (.getFromCache
      runResultsCache
      ^Class (-> fixture (.getFixtureType) (.getFixtureClass))
      nil)
    (.getModifiedResultSummary)))

(defn print-result [^ResultSummary res ^FixtureDeclarations ftype]
  (.print res System/out ftype))

(defn ^ResultSummary run-specification
  "The entry point into testing a specification, represented by the given `fixture`.
   See org.concordion.integration.junit4.ConcordionRunner.run
   "
  [^Fixture fixture suite?]
  {:post [(instance? ResultSummary %)]}
  (if-let [cached-res (cached-spec-result fixture)]
    cached-res
    (let [ftype (.getFixtureType fixture)
          runner (FixtureRunner.
                   fixture
                   specification->fixture-locator)
          concordion (doto (.getConcordion runner)
                       (.checkValidStatus ftype))]
      (try
        (do
          (.setupForRun fixture (.getFixtureObject fixture))
          (when suite? (.beforeSuite fixture))
          (.startFixtureRun runResultsCache ftype (.getSpecificationDescription concordion))
          (.beforeSpecification fixture)
          (run-fixture-examples fixture runner concordion)) ;; run and cache results
        (doto (cached-spec-result fixture)
          (print-result ftype))
        (finally
          (.afterSpecification fixture)
          (.finish concordion)
          (when suite? (.afterSuite fixture)))))))

;;---------------------------------------------------------------------- concordion:run command

(defn- concordion-run
  "Support for concordion:run, invoked e.g. with
   [(org.concordion.api.Resource. \"/math/Algebra.html\") \"./algebra/Addition.md\"]"
  [resource href]
  (let [fixture-var (specification->fixture resource href)]
    (run-specification
      (new-fixture
        fixture-var
        @fixture-var)
      ;; Concordion runs before/after suite only for top pages, not those referenced via concordion:run
      false)))

(defn- assert-type [obj ^Class type]
  (assert
    (instance? type obj)
    (str "Not instance of " type "; value = " obj))
  obj)

;; Runner implementation to be registered with Concordion, responsible for handling concordion:run commands
(defrecord ClojureTestRunner []
  Runner
  ;; ResultSummary execute(Resource resource, String href) throws Exception;
  (execute [_ resource href]
    (doto (concordion-run resource href)
      (assert-type ResultSummary))))

;;---------------------------------------------------------------------- the deffixture macro & friends

(defn assert-test-ns [ns]
  (assert
    (cs/ends-with? (name (ns-name ns)) "-test")
    "The namespace using `deffixture` must end in -test so that clojure.test will find and run the generated test."))

(defn- deffixture*
  [name opts]
  {:pre [(or (symbol? name) #_(string? name))]}
  (assert-test-ns *ns*)
  (let [var-sym name]
    `(do
       (def ~var-sym ~opts)
       (test/deftest ~(symbol (str var-sym "-test"))
         (let [fixture# (new-fixture (var ~var-sym) ~opts)
               result#  (run-specification fixture# true)]
           (do (swap! fixtures conj fixture#))
           (when (zero? (+
                          (.getSuccessCount result#)
                          (.getExceptionCount result#)
                          (.getFailureCount result#)))
             (println (str "Warning: The specification  with the fixture " (var ~var-sym)
                           " seems to have no asserts.")))
           (test/is (zero? (.getExceptionCount result#)))
           (test/is (zero? (.getFailureCount result#))))))))


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
     - opts   - options defined by org.concordion.api.FixtureDeclarations, as qualified keywords

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

(do
  ;; Set our runner (for the "concordion:run" command) as the default runner:
  (System/setProperty "concordion.runner.concordion" (.getName ClojureTestRunner)))



