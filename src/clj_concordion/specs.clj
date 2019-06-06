(ns clj-concordion.specs
  (:require [clojure.spec.alpha :as s])
  (:import (org.concordion.api.option MarkdownExtensions)
           (org.concordion.api ImplementationStatus)))

(def impl-status {:expected-to-pass ImplementationStatus/EXPECTED_TO_PASS
                  :expected-to-fail ImplementationStatus/EXPECTED_TO_FAIL
                  :ignored          ImplementationStatus/IGNORED
                  :unimplemented    ImplementationStatus/UNIMPLEMENTED})

;; Fixture options from FixtureDeclarations
(s/def :concordion/full-ognl boolean?)
(s/def :concordion/fail-fast boolean?)
(s/def ::exception-class (s/and class? #(isa? % Throwable)))
(s/def :concordion/fail-fast-exceptions (s/coll-of ::exception-class))
(s/def :concordion/impl-status (-> impl-status keys set))
;; From ConcordionOptions:
(s/def :concordion.option/markdown-extensions (s/coll-of #(instance? MarkdownExtensions %)))
(s/def :concordion.option/copy-source-html-to-dir string?)
(s/def :concordion.option/declare-namespaces (s/coll-of string?))
(s/def :cc/before-suite fn?)
(s/def :cc/before-spec fn?)
(s/def :cc/before-example fn?)
(s/def :cc/after-example fn?)
(s/def :cc/after-spec fn?)
(s/def :cc/after-suite fn?)
(s/def :cc/opts
  (s/and
    (s/keys :opt [:concordion/full-ognl
                  :concordion/fail-fast
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
                  :cc/after-suite])
    (s/every-kv #{:concordion/full-ognl
                  :concordion/fail-fast
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
                  :cc/after-suite}
                (constantly true))))

(s/def :cc/classname (s/or :str string? :sym symbol?))
(s/def :cc/methods (s/coll-of symbol?))
