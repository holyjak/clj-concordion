(ns clj-concordion.specs
  (:require [clojure.spec.alpha :as s])
  (:import (org.concordion.api.option MarkdownExtensions)
           (org.concordion.api ImplementationStatus)))

(def impl-status {:expected-to-pass ImplementationStatus/EXPECTED_TO_PASS
                  :expected-to-fail ImplementationStatus/EXPECTED_TO_FAIL
                  :ignored          ImplementationStatus/IGNORED
                  :unimplemented    ImplementationStatus/UNIMPLEMENTED})

;;;; Fixture options from FixtureDeclarations ;;;;
;; Fail fast upon the first exception if true|:all|:exceptions (a Throwable) and/or
;; the first test failure if true|:all|:failures
(s/def :concordion/fail-fast (s/or
                               :bool boolean?
                               :kwd #{:failures :exceptions}))
(s/def ::exception-class (s/and class? #(isa? % Throwable)))
;; A collection of the exceptions that should stop the tests at once; implies `:concordion/fail-fast :exceptions`
(s/def :concordion/fail-fast-exceptions (s/coll-of ::exception-class :min-count 1 :distinct true))
(s/def :concordion/impl-status (-> impl-status keys set))
;; From ConcordionOptions:
(s/def :concordion.option/markdown-extensions (s/coll-of #(instance? MarkdownExtensions %)))
(s/def :concordion.option/copy-source-html-to-dir string?)
(s/def :concordion.option/declare-namespaces (s/coll-of string? :kind sequential?))
(s/def :cc/before-suite fn?)
(s/def :cc/before-spec fn?)
(s/def :cc/before-example fn?)
(s/def :cc/after-example fn?)
(s/def :cc/after-spec fn?)
(s/def :cc/after-suite fn?)
(s/def :cc/no-asserts? boolean?)
(s/def :cc/no-trim? boolean?)
(s/def :cc/opts
  (s/and
    (s/keys :opt [:concordion/fail-fast
                  :concordion/fail-fast-exceptions
                  :concordion/impl-status
                  :concordion.option/markdown-extensions
                  :concordion.option/copy-source-html-to-dir
                  :concordion.option/declare-namespaces
                  :cc/before-suite
                  :cc/before-spec
                  :cc/before-example
                  :cc/after-example
                  :cc/after-spec
                  :cc/after-suite
                  :cc/no-asserts?
                  :cc/no-trim?])
    ; Make the spec "closed" by preventing any unknown (mistyped?) key:
    (s/map-of #{:concordion/fail-fast
                :concordion/fail-fast-exceptions
                :concordion/impl-status
                :concordion.option/markdown-extensions
                :concordion.option/copy-source-html-to-dir
                :concordion.option/declare-namespaces
                :cc/before-suite
                :cc/before-spec
                :cc/before-example
                :cc/after-example
                :cc/after-spec
                :cc/after-suite
                :cc/no-asserts?
                :cc/no-trim?}
              (constantly true))))

(s/def :cc/classname (s/or :str string? :sym symbol?))

(s/def :expr/arg (s/or
                   :variable symbol?
                   :string   string?
                   :number   number?
                   :boolean  boolean?
                   :keyword  keyword?
                   :nil      nil?
                   :vector   (s/coll-of :expr/arg :kind vector?)))
(s/def ::call-expr (s/cat
                     :function symbol?
                     :arguments (s/coll-of :expr/arg :kind sequential?)))
;; FIXME Shouldn't Concordion distinguish `#result`, `#result = myfn()`, `myfn()` and call `setVariable` for the first two instead of eval.?
;; Ex.: `#result = add(1, 2)` => `[result = add 1 2]`
(s/def ::assign-expr (s/cat
                       :variable symbol?
                       :equals #{'=}
                       :call-expr ::call-expr))
(s/def ::expr (s/or
                :single-var (s/cat :variable symbol?)
                :call-expr ::call-expr
                :assign-expr ::assign-expr))
