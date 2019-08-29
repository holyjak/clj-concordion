(ns clj-concordion.internal.run
  (:require
    [clj-concordion.internal.utils :refer :all]
    [clj-concordion.internal.interop :refer :all]
    [clojure.string :as cs]
    [clojure.tools.logging :as log])
  (:import
    (org.concordion Concordion)
    (org.concordion.api Fixture FixtureDeclarations ResultSummary Runner)
    (org.concordion.internal FixtureRunner
                             FailFastException RunOutput)
    (org.concordion.internal.cache RunResultsCache)))

(def ^RunResultsCache runResultsCache RunResultsCache/SINGLETON)

;;---------------------------------------------------------------------- running

(defn base-example-name
  "Drop ? and all after that, if present"
  [example]
  (cs/replace example #"\?.*$" ""))

(defn assert-unique-examples [^Fixture fixture examples]
  (let [uniq (->> examples (map base-example-name) set)
        dupl (vec (for [[example freq] (frequencies examples) :when (> freq 1)] example))]
    (when (> (count examples) (count uniq))
      (throw
        (ex-info (str "Specification contains non-unique example names. Duplicates: "
                      dupl " (All: " (vec examples) ")")
                 {:repeated              dupl
                  :all                   examples
                  :specification         (-> fixture .getFixtureType .getFixturePathWithoutSuffix)
                  :generated-fixture-var (.getFixtureObject fixture)})))))

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
    (assert-unique-examples fixture examples)
    (log/debug "run-fixture-examples: examples found in " (.getFixtureObject fixture) ": " (str examples))
    (doall
      (map
        #(do
           (.beforeExample fixture %)
           (try
             (doto (.run runner % fixture)
               (.assertIsSatisfied ftype))
             (catch Throwable e
               ;; Ignore all non FailFastExc; they are already
               ;; recorded in the results summary
               (if (instance? FailFastException e)
                 (throw e)
                 (log/info
                   (when-not (instance? org.concordion.internal.ConcordionAssertionError e)
                     e)
                   "Example '" % "' failed with the exception <" e ">. Continuing because it is not configured in `:concordion/fail-fast[-exceptions]`; the test will be marked as failed.")))
             (finally
               (.afterExample fixture %))))
        examples))))

(defn ^ResultSummary cached-spec-result
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
    (let [ftype      (.getFixtureType fixture)
          runner     (FixtureRunner.
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
        (catch Exception e
          (.failRun runResultsCache ftype nil) ;; nil == the whole spec, not a particular example
          (throw e))
        (finally
          (.afterSpecification fixture)
          (.finish concordion)
          (when suite? (.afterSuite fixture)))))))

;;---------------------------------------------------------------------- concordion:run command

(defn concordion-run
  "Support for concordion:run, invoked e.g. with
   [(org.concordion.api.Resource. \"/math/Algebra.html\") \"./algebra/Addition.md\"]
   `href` is a path to a spec relative to the `resource`'s parent dir"
  [resource href]
  (let [fixture-var (specification->fixture resource href)]
    (run-specification
      (new-fixture
        fixture-var
        @fixture-var)
      ;; Concordion runs before/after suite only for top pages, not those referenced via concordion:run
      false)))

(defn assert-type [obj ^Class type]
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

(do
  ;; Set our runner (for the "concordion:run" command) as the default runner:
  (System/setProperty "concordion.runner.concordion" (.getName ClojureTestRunner)))