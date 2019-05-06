(ns clj-concordion.core
  (:require
    [clj-concordion.internal.utils :refer :all]
    [clojure.test :as test]
    [clojure.string :as str])
  (:import
    [org.concordion.internal ClassNameBasedSpecificationLocator
                             FixtureInstance
                             FixtureRunner FixtureType]
    (org.concordion.api Resource Fixture FixtureDeclarations ImplementationStatus)
    (org.concordion.api.option ConcordionOptions MarkdownExtensions)))

(defn- before-suite [fix-inst suite?]
  (when suite?
    (.beforeSuite fix-inst)))


(defn run-fixture
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
    (some
      #(try
         (Class/forName %)
         (catch ClassNotFoundException _ nil))
      variants)))

(defn- rm-fixture-suffix [^String fixture-name]
  (str/replace fixture-name #"(Fixture|Test)$" ""))

(deftype CljFixtureType [fixture-obj] ;; FIXME Make subclass of FixtureType - use `proxy`
  FixtureDeclarations
  ;; TODO Enable users to actually set these settings
  (getFixtureClass [_] (.getClass fixture-obj))
  (declaresFullOGNL [_] false)
  (declaresFailFast [_] false)
  (getDeclaredFailFastExceptions [_] (into-array Class []))
  (declaresResources [_] false)
  (getDeclaredImplementationStatus [_] ImplementationStatus/EXPECTED_TO_PASS)
  (getDeclaredConcordionOptionsParentFirst [_]
    [(reify ConcordionOptions
       (markdownExtensions [_] (into-array MarkdownExtensions []))
       (copySourceHtmlToDir [_] "")
       (declareNamespaces [_] (into-array String [])))])
  (getFixturePathWithoutSuffix [this] (-> (.getFixtureClass this)
                                          (.getName)
                                          (str/replace #"\." "/")
                                          (rm-fixture-suffix)))
  (getDescription [this] (format "[Concordion Specification for '%s']"
                                 (-> (.getFixtureClass this)
                                     (.getSimpleName)
                                     (rm-fixture-suffix)))))

(defn- wrap-with-fixture-type
  "TMP: Corrently Fixture.getFixtureType returns FixtureType instead of
   FixtureDeclarations. Until changed we have to enable that.
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

(deftype CljFixture [fixture-obj ^FixtureType fixture-type]
  Fixture
  ;; TODO Enable users to specify before/after* functions
  (getFixtureObject [_] fixture-obj)
  (getFixtureType [_] fixture-type)
  (setupForRun [_ _] nil)
  (beforeSuite [_] nil)
  (afterSuite [_] nil)
  (beforeSpecification [_] nil)
  (afterSpecification [_] nil)
  (beforeProcessExample [_ _] nil)
  (beforeExample [_ _] nil)
  (afterExample [_ _] nil)
  (afterProcessExample [_ _] nil))

(defn- new-fixture*
  "Give a user-provided fixture class such as math.Algebra, wrap it in the
   types required by Concordion"
  [^Class fixture-class]
  (let [fixture-obj (.newInstance fixture-class)
        fixture-type (wrap-with-fixture-type
                       (CljFixtureType. fixture-obj))]
    (CljFixture. fixture-obj fixture-type)))

;; Memoized new-fixture* so that we always get the same instance for the
;;   same class and thus caching of run results in Concordion will work.
(def new-fixture (memoize new-fixture*))

(defrecord ClojureTestRunner []
  org.concordion.api.Runner
  ;; ResultSummary execute(Resource resource, String href) throws Exception;
  (execute [_ resource href]
    (run-fixture
      (new-fixture (find-fixture-class resource href))
      ;; Concordion runs before/after suite only for top pages, not those referenced via concordion:run
      false)))

(defn- deffixture*
  [name methods]
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
       (gen-class
         :name ~class-name
         :methods ~methods*
         :prefix ~prefix)
       (test/deftest ~(symbol (str prefix "test"))
         (run-fixture (new-fixture ~(symbol class-name)) true)))))

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

   Example:
     Given the spec math/Addition.md with `yields [4](- \"?=add(#n1, #n2)\")`
     write `(defn ^int add [^int n1, ^int n2] (+ n1 n2)` and expose it to the spec. with:
     `(deffixture math.AdditionFixture [add])`.

   See [concordion instrumenting](https://concordion.org/instrumenting/java/markdown/) and
   [coding docs](https://concordion.org/coding/java/markdown/) for more details."
  [name methods]
  (deffixture* name (map resolve methods)))

(do
  ;; Set our runner (for the "concordion:run" command) as the default runner:
  (System/setProperty "concordion.runner.concordion" (.getName ClojureTestRunner)))