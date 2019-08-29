(ns clj-concordion.internal.interop
  (:require
    [clj-concordion.specs :as specs]
    [clojure.edn :as edn]
    [clojure.string :as cs]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.tools.logging :as log]
    [clj-concordion.specs :as ccs])
  (:import (org.concordion.api.extension Extensions ConcordionExtension)
           (org.concordion.api.option ConcordionOptions MarkdownExtensions)
           (org.concordion.internal ConcordionBuilder FixtureType)
           (org.concordion.api EvaluatorFactory Evaluator SpecificationLocator Resource FixtureDeclarations Fixture)))

;;---------------------------------------------------------------------- Fixture <-> Specification mapping

(defn- drop-file-suffix [path]
  (subs path 0 (.lastIndexOf path ".")))

(defn path->var-symbol
  "From \"/my/awesome-ns/MySpec.md\" to 'my.awesome-ns-test/MySpec."
  [^String path]
  (let [segments (->> (cs/split path #"/")
                      (remove cs/blank?))
        ns       (str
                   (cs/join "." (butlast segments))
                   "-test")
        name     (drop-file-suffix
                   (last segments))]
    (symbol ns name)))

(defn var->path
  "From #'my.awesome-ns/MySpec to  \"/my/awesome-ns/MySpec.md\".
   Args:
    - v the var to map
    - type-suffix: (md|html|nil) where nil means append no suffix"
  ([v] (var->path v "md"))
  ([v type-suffix]
   {:pre [(var? v)]}
   (let [{:keys [name ns]} (meta v)
         dir (-> (str (ns-name ns))
                 (cs/replace "." "/")
                 (cs/replace #"-test$" ""))]
     (str "/" dir "/" name
          (when type-suffix
            (str "." type-suffix))))))

(defn specification->fixture [^Resource resource ^String href]
  ;; See DefaultConcordionRunner.findTestClass
  ;; [(org.concordion.api.Resource. "/math/Algebra.html") "./algebra/Addition.md"]
  (let [var-sym (-> resource
                    (.getParent)
                    (.getRelativeResource href)
                    (.getPath)
                    (path->var-symbol))]
    (or
      (try
        (log/debug "specification->fixture(" resource href "-> " var-sym)
        (find-var var-sym)
        (catch Exception e
          (throw (RuntimeException.
                   (str "Expected to find var '" var-sym "' for " resource " and href='"
                        href "' but failed with " e)))))
      (throw (RuntimeException. (str "No var '" var-sym "' found for " resource " and href='" href "'"))))))

(def specification->fixture-locator
  (reify SpecificationLocator
    (locateSpecification [_ fixtureDeclarations typeSuffix]
      (Resource.
        (str
          (.getFixturePathWithoutSuffix fixtureDeclarations)
          "." typeSuffix)))))

;;---------------------------------------------------------------------- Expr evaluation

(defn conform-or-fail [spec val]
  (let [res (s/conform spec val)]
    (if (#{:clojure.spec.alpha/invalid} res)
      (throw (ex-info (str "The value `" val "` does not conform to the spec : "
                           (s/explain-str spec val))
                      (s/explain-data spec val)))
      res)))

(defn edn->data
  "Parse EDN with a user-friendly error handling into data."
  [expr]
  (try
    (edn/read-string expr)
    (catch Exception e
      (ex-info
        (format "Failed to parse expr `%s` as EDN (expected: `function-name(#arg1, ...)`: %s"
                expr e)
        {:expr expr :err e}))))

(defn resolve-arg [vars var-sym idx]
  (let [res (get vars var-sym ::none)]
    (if (#{::none} res)
      (throw (ex-info (format "Unknown argument `%s` at position %s (0-based). Known: %s"
                              var-sym idx (or (keys vars) "<no vars>"))
                      {:vars vars :var-sym var-sym :idx idx}))
      res)))


(defn expr->edn
  "Sanitize a Concordion function call expression so that it is a valid EDN string."
  [expr]
  ;; 1. Wrap in a vector since we have pair `symbol arg-list` so EDN would only read the 1st.
  (-> (str "[" expr "]")
      ;; 2. Remove '#' so the reader doesn't blow up; symbols will be good enough
      (cs/replace #"#" "")
      ;; 3. Replace nested '..' with ".." so that we can embed literal strings in Markdown
      ;; (Concordion always outputs ".." around the command as of 2.2.0, see org.pegdown.ToHtmlSerializer.printAttribute
      (cs/replace #"\'" "\"")
      ;; 4. Ensure there is space around `=` so that `#result=fnCall()` will not make a single fn name `result=fnCall`
      (cs/replace #"=" " = ")))

(defn sym->qualified [ns unqualified-sym]
  (symbol ns (name unqualified-sym)))

(defn parse-expr-data
  "Ex.inputs from spec/conform:
  1) `[:assign-expr {:variable out, :equals =, :call-expr {:function myfn, :arguments ([:number 1] [:number 2])}}]`
  2) `[:call-expr {:function myfn, :arguments ([:number 1] [:number 2])}]`"
  [expr-edn]
  (let [[tag parsed] (conform-or-fail ::specs/expr expr-edn)
        call-expr (or (:call-expr parsed) parsed)]
    (if (= tag :single-var)
      {:single-var (:variable parsed)}
      (assoc call-expr
        :set-var (:variable parsed)))))

(comment

  (->> "myfn(:has, [:vector])"
       expr->edn
       edn->data
       #_(conform-or-fail ::specs/expr)
       parse-expr-data
       :arguments)

  nil)

(defn resolve-args
  "Resolve the arguments inside an expression's function call, given the known variables `vars`"
  ([vars arguments]
   (resolve-args [] vars arguments))
  ([path vars arguments]
   (map-indexed
     (fn [idx [tag val]]
       ;; entry is something like [:number 32], [:string "hi"], [:variable myvar], ...
       (let [path' (conj path idx)]
         (case tag
           :variable (resolve-arg vars val path')
           :vector (vec (resolve-args path' val))
           ; Default: Just return the (presumably primitive) value:
           val)))
     arguments)))

(def exposed-clj-fns
  "Core functions we make directly usable in expressions"
  {'get #'clojure.core/get
   'get-in #'clojure.core/get-in})

(defn evaluate
  "Evaluate expressions from specifications such as `add(#arg1, #arg2)` given
   previously stored variables.
   See `org.concordion.internal.OgnlEvaluator` for the original evaluator.

   Params:
   - `vars-atom` atom containing a map with known variables (symbol -> value)
   - `ns` - the namespace where to look for symbols (functions) used in the expression
   - `expr` - the expression"
  [vars-atom ^String ns ^String expr]
  (try
    (let [vars @vars-atom
          expr-data   (-> expr expr->edn edn->data)
          {:keys [function arguments set-var single-var]} (parse-expr-data expr-data)
          arg-vals (resolve-args vars arguments)
          fn-var   (or
                     single-var
                     (find-var (sym->qualified ns function))
                     ;; Default functions supported
                     (get exposed-clj-fns function)
                     (throw (ex-info (format "Could not find the expected function `%s/%s`"
                                             ns function)
                                     {:expr expr :ns ns :function function})))
          result   (if single-var
                     (get vars single-var)
                     (apply @fn-var arg-vals))]
      (log/debug "(evaluate " ns ", " expr ") => " result)
      (when set-var
        (swap! vars-atom assoc set-var result))
      result)
    (catch Exception e
      (throw (ex-info (format "Failed to evaluate expr `%s` due to: %s with the variables %s"
                              expr e @vars-atom)
                      {:expr expr :data (ex-data e) :err e}
                      e)))))

(comment
  (defn _add [n] n)
  (evaluate (atom {'n1 10}) "clj-concordion.internal.interop" "_add(n1)")
  (evaluate (atom {'n1 nil}) "clj-concordion.internal.interop" "_add(n1)")
  nil)

(s/fdef evaluate
        :args (s/cat :vars-ref #(instance? clojure.lang.Atom %) :ns string? :expr string?)
        :ret any?)

(defn concord-var->sym
  "`#var` -> `var`"
  [name]
  (symbol
    (cs/replace-first name #"^#" "")))

(defn new-eval-factory []
  (reify EvaluatorFactory
    (createEvaluator [_ fixture]
      (let [fix-var (.getFixtureObject fixture)
            opts     @fix-var
            vars    (atom {})
            ns      (name (ns-name (:ns (meta fix-var))))]
        (prn ns)
        (assert (var? fix-var) "Fixture object must be a Clojure var")
        (reify Evaluator
          (getVariable [_ name] (get @vars (concord-var->sym name)))
          (setVariable [_ name value]
            (swap! vars assoc
                   (concord-var->sym name)
                   (if (:cc/no-trim? opts)
                     value
                     (some-> value clojure.string/trim))))
          (evaluate [_ expr]
            (try
              (evaluate vars ns expr)
              (catch Exception e
                (if-let [spec-err (-> e ex-data :clojure.spec.alpha/problems first)]
                  (throw (ex-info
                           (str "Call to evaluate with expr=`"expr"` did not conform to the spec:" spec-err)
                           {:spec-err spec-err}
                           e))
                  (throw e))))))))))

;;---------------------------------------------------------------------- Reconfigure Concordion via the Extension API

(deftype CljConcordionExtension []
  ConcordionExtension
  (addTo [_ concordionExtender]
    (assert (instance? ConcordionBuilder concordionExtender) "Expected the concordionExtender to be a ConcordionBuilder, which only has the method we need")
    #_(.withSpecificationLocator concordionExtender nil)
    (.withEvaluatorFactory ^ConcordionBuilder concordionExtender (new-eval-factory))))

;; A type to hold fixture-level annotations, when there is no other way to configure Concordion
;; Returned from fixtureType.getFixtureClass()
(deftype
  ^{Extensions [CljConcordionExtension]}
  CljConcordionFixtureAnnotationCarrier [])

;; TODO 1) What is a fixture? How to make it? A: A var with options: (def AlgebraFixture {:cc/before-example noop, ...})
;; TODO 1) How to map ns (fixture) <-> specification.md?

;;---------------------------------------------------------------------- Fixture Concordion integration classes

(deftype CljFixtureType [fixture-var opts]
  FixtureDeclarations
  (getFixtureClass [_] (:cc/class (meta fixture-var)))
  (declaresFullOGNL [_] false) ;; We don't use the default OGNL evaluator so it has no meaning
  (declaresFailFast [_] (get opts :concordion/fail-fast false))
  (getDeclaredFailFastExceptions [_] (into-array Class (get opts :concordion/fail-fast-exceptions [])))
  (declaresResources [_] false) ;; FIXME Unusable without annotations; change in API to return a list of @ConcordionResources ? - talk to devs
  (getDeclaredImplementationStatus [_] (ccs/impl-status (get opts :concordion/impl-status :expected-to-pass)))
  (getDeclaredConcordionOptionsParentFirst [_]
    [(reify ConcordionOptions
       (markdownExtensions [_] (into-array MarkdownExtensions (get opts :concordion.option/markdown-extensions [])))
       (copySourceHtmlToDir [_] (get opts :concordion.option/copy-source-html-to-dir ""))
       (declareNamespaces [_] (into-array String (get opts :concordion.option/declare-namespaces []))))])
  (getFixturePathWithoutSuffix [_] (var->path fixture-var nil))
  (getDescription [_] (format "[Concordion Specification for '%s']"
                              (:name (meta fixture-var))))
  Object
  (toString [_] (str (:name (meta fixture-var)))))

(defn- wrap-with-fixture-type
  "TMP(hopefully): Currently Fixture.getFixtureType returns FixtureType instead of
   FixtureDeclarations. Until changed we have to support that.
   Afterwards we can use CljFixtureType directly."
  [^CljFixtureType my-fix-type]
  (proxy [FixtureType] [(.getFixtureClass my-fix-type)]
    (getFixtureClass [] (.getFixtureClass my-fix-type))
    (declaresFullOGNL [] (.declaresFullOGNL my-fix-type))
    (declaresFailFast [] (.declaresFailFast my-fix-type))
    (getDeclaredFailFastExceptions [] (.getDeclaredFailFastExceptions my-fix-type))
    (declaresResources [] (.declaresResources my-fix-type))
    (getDeclaredImplementationStatus [] (.getDeclaredImplementationStatus my-fix-type))
    (getDeclaredConcordionOptionsParentFirst [] (.getDeclaredConcordionOptionsParentFirst my-fix-type))
    (getFixturePathWithoutSuffix [] (.getFixturePathWithoutSuffix my-fix-type))
    (getDescription [] (.getDescription my-fix-type))
    (getClassHierarchyParentFirst [] [CljConcordionFixtureAnnotationCarrier])))

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
  (afterProcessExample [_ _] nil)
  Object
  (toString [_] (str "Wrapper for " fixture-obj)))

(defn- new-fixture*
  "Given a user-provided fixture object, wrap it in the
   types required by Concordion"
  [fixture-var opts]
  {:pre [(var? fixture-var)]}
  (let [fixture-type (wrap-with-fixture-type
                       (CljFixtureType. fixture-var opts))]
    (CljFixture. fixture-var fixture-type opts)))

(s/fdef new-fixture*
        :args (s/cat :fixture-var var?, :opts (s/nilable :cc/opts)))

;; Memoized new-fixture* so that we always get the same instance for the
;;   same class and thus caching of run results in Concordion will work.
(def new-fixture (memoize new-fixture*))

;;------------------------------------------------------------------ Error discovery
(st/instrument
  (filter
    #(#{(name (ns-name *ns*))} (namespace %))
    (st/instrumentable-syms)))