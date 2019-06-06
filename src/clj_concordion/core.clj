(ns clj-concordion.core
  (:require
    [clj-concordion.internal.utils :refer :all]
    [clj-concordion.specs :as ccs]
    [clojure.test :as test]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.string :as cs])
  (:import
    (org.concordion Concordion)
    (org.concordion.api Resource Fixture FixtureDeclarations ResultSummary Runner)
    (org.concordion.api.option ConcordionOptions MarkdownExtensions)
    (org.concordion.internal FixtureRunner
                             FixtureType
                             ClassNameBasedSpecificationLocator)
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
                ;; TODO Ignore all non-FailFastException excs
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
    (.getFromCache
      runResultsCache
      (-> fixture (.getFixtureType) (.getFixtureClass))
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
                   (ClassNameBasedSpecificationLocator.))
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

;;---------------------------------------------------------------------- fixture & c:run helpers

(defn- drop-file-suffix [path]
  (subs path 0 (.lastIndexOf path ".")))

(defn- find-fixture-class [^Resource resource ^String href]
  ;; See DefaultConcordionRunner.findTestClass
  (let [base-name (-> resource
                      (.getParent)
                      (.getRelativeResource href)
                      (.getPath)
                      (.replaceFirst "/" "")
                      (.replace "/" ".")
                      (drop-file-suffix))
        variants (map
                   #(str base-name %)
                   [nil "Test" "Fixture"])]
    (or
      (some
        #(try
           (Class/forName %)
           (catch ClassNotFoundException _ nil))
        variants)
      (throw
        (NoClassDefFoundError.
          (str "could not find any of possible fixture classes " base-name "[Test|Fixture]. Perhaps you forgot to AOT-compile the test namespace or the output is not on the classpath?"))))))

(defn- rm-fixture-suffix [^String fixture-name]
  (cs/replace fixture-name #"(Fixture|Test)$" ""))

;;---------------------------------------------------------------------- Fixture Concordion integration classes

(deftype CljFixtureType [fixture-obj opts]
  FixtureDeclarations
  (getFixtureClass [_] (.getClass fixture-obj))
  (declaresFullOGNL [_] (get opts :concordion/full-ognl false))
  (declaresFailFast [_] (get opts :concordion/fail-fast false))
  (getDeclaredFailFastExceptions [_] (into-array Class (get opts :concordion/fail-fast-exceptions [])))
  (declaresResources [_] false) ;; FIXME Unusable without annotations; change in API to return a list of @ConcordionResources ? - talk to devs
  (getDeclaredImplementationStatus [_] (ccs/impl-status (get opts :concordion/impl-status :expected-to-pass)))
  (getDeclaredConcordionOptionsParentFirst [_]
    [(reify ConcordionOptions
       (markdownExtensions [_] (into-array MarkdownExtensions (get opts :concordion.option/markdown-extensions [])))
       (copySourceHtmlToDir [_] (get opts :concordion.option/copy-source-html-to-dir ""))
       (declareNamespaces [_] (into-array String (get opts :concordion.option/declare-namespaces []))))])
  (getFixturePathWithoutSuffix [this] (-> (.getFixtureClass this)
                                          (.getName)
                                          (cs/replace #"\." "/")
                                          (rm-fixture-suffix)))
  (getDescription [this] (format "[Concordion Specification for '%s']"
                                 (-> (.getFixtureClass this)
                                     (.getSimpleName)
                                     (rm-fixture-suffix)))))

(defn- wrap-with-fixture-type
  "TMP(hopefully): Currently Fixture.getFixtureType returns FixtureType instead of
   FixtureDeclarations. Until changed we have to support that.
   Afterwards we can use CljFixtureType directly."
  [my-fix-type]
  (proxy [FixtureType] [(.getFixtureClass my-fix-type)]
    (getFixtureClass [] (.getFixtureClass my-fix-type))
    (declaresFullOGNL [] (.declaresFullOGNL my-fix-type))
    (declaresFailFast [] (.declaresFailFast my-fix-type))
    (getDeclaredFailFastExceptions [] (.getDeclaredFailFastExceptions my-fix-type))
    (declaresResources [] (.declaresResources my-fix-type))
    (getDeclaredImplementationStatus [] (.getDeclaredImplementationStatus my-fix-type))
    (getDeclaredConcordionOptionsParentFirst [] (.getDeclaredConcordionOptionsParentFirst my-fix-type))
    (getFixturePathWithoutSuffix [] (.getFixturePathWithoutSuffix my-fix-type))
    (getDescription [] (.getDescription my-fix-type))))

(deftype CljFixture [fixture-obj ^FixtureType fixture-type opts]
  Fixture
  (getFixtureObject [_] fixture-obj)
  (getFixtureType [_] fixture-type)
  (setupForRun [_ _] nil)
  (beforeSuite [_] (when-let [f (:cc/before-suite opts)] (f)))
  (afterSuite [_] (when-let [f (:cc/after-suite opts)] (f)))
  (beforeSpecification [_] (when-let [f (:cc/before-spec opts)] (f)))
  (afterSpecification [_] (when-let [f (:cc/after-spec opts)] (f)))
  (beforeProcessExample [_ _] nil)
  (beforeExample [_ ex] (when-let [f (:cc/before-example opts)] (f ex)))
  (afterExample [_ ex] (when-let [f (:cc/after-example opts)] (f ex)))
  (afterProcessExample [_ _] nil))

(defn- new-fixture*
  "Give a user-provided fixture class such as math.Algebra, wrap it in the
   types required by Concordion"
  [fixture-class-name opts]
  (let [fixture-class (try
                        (Class/forName fixture-class-name)
                        (catch ClassNotFoundException e
                          (throw (ClassNotFoundException.
                                   (str "Fixture class " fixture-class-name
                                        " not found; did you forgot to AOT-compile the test namespace(s) or add the output to the classpath?")
                                   e))))
        fixture-obj (.newInstance fixture-class)
        fixture-type (wrap-with-fixture-type
                       (CljFixtureType. fixture-obj opts))]
    (CljFixture. fixture-obj fixture-type opts)))

(s/fdef new-fixture*
        :args (s/cat :fixture-class-name string?, :opts (s/nilable :cc/opts)))

(st/instrument `new-fixture*)

;; Memoized new-fixture* so that we always get the same instance for the
;;   same class and thus caching of run results in Concordion will work.
(def new-fixture (memoize new-fixture*))

;;---------------------------------------------------------------------- concordion:run command

(defn- concordion-run
  "Support for concordion:run, invoked e.g. with
   [(org.concordion.api.Resource. \"/math/Algebra.html\") \"./algebra/Addition.md\"]"
  [resource href]
  (let [fixture-class (find-fixture-class resource href)]
    (run-specification
      (new-fixture
        (.getName fixture-class)
        (._opts (.newInstance fixture-class)))
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

(defn- deffixture*
  [name methods opts]
  {:pre [(or (symbol? name) (string? name))
         (sequential? methods)
         (every? var? methods)]}

  (let [class-name (clojure.core/name name)
        prefix (str (.replaceAll class-name "\\." "-") "-")
        methods* (->> methods
                      (map var->method-descr)
                      (mapv (juxt :namesym :args :ret)))
        ;; implementation functions for the methods:
        defns (map
                (partial ->defn prefix)
                methods)]
    `(do
       ~@defns
       (defn ~(symbol (str prefix "_opts")) [~'_]
         ~opts)
       (gen-class
         :name ~class-name
         :methods ~(conj
                     methods*
                     '[_opts [] java.util.Map])
         :prefix ~prefix)
       (test/deftest ~(symbol (str prefix "test"))
         (let [fixture# (new-fixture ~class-name ~opts)
               result# (run-specification fixture# true)]
           (do (swap! fixtures conj fixture#))
           (when (zero? (+
                          (.getSuccessCount result#)
                          (.getExceptionCount result#)
                          (.getFailureCount result#)))
             (println (str "Warning: The specification  with the fixture " ~class-name
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
  [name methods & more]
  (let [[opts] more
        opts2check (eval opts)]
    (when opts2check
      ;; Fail fast to give err message early to the user
      (s/assert :cc/opts opts2check))
    (deffixture*
      name
      (map resolve methods)
      opts)))

(s/fdef deffixture
        :args (s/cat :name :cc/classname
                     :methods :cc/methods
                     :opts (s/? map?)))

(do
  ;; Set our runner (for the "concordion:run" command) as the default runner:
  (System/setProperty "concordion.runner.concordion" (.getName ClojureTestRunner)))



