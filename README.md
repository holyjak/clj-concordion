# clj-concordion

Add support for Clojure and `clojure.test` to  [Concordion](https://concordion.org/),
a Specification By Example tool.

With Concordion, you can write high-level specification of features in Markdown
and document them with examples. Hidden instrumentation (in the form of magic links)
binds the examples to functions in your Fixture classes making it possible to verify
them against the code. When you run your test runner, Concordion does generate 
HTML files incorporating results of the passed or failed examples/tests.

![](https://concordion.org/img/how-it-works-markdown.png)

## Usage

### Preparation

Add a dependency on this project:

[![Clojars Project](https://img.shields.io/clojars/v/clj-concordion.svg)](https://clojars.org/clj-concordion)

```
[clj-concordion "0.0.2"]
```

```
clj-concordion {:mvn/version "0.0.2"}
```

and configure your tests so that the test namespaces are compiled
(which is necessary since we need to generate fixture classes). With lein:

```clojure
;; project.clj
(defproject ...
  :profiles {:test {:aot [mypapp.core-test] }} ;; Or a RegExp for all fixture test namespaces
)
```

### Coding

Given the Concordion specification `<class path>/math/Addition.md` containing:

```markdown
Adding [1](- "#n1") and [3](- "#n2") yields [4](- "?=add(#n1, #n2)").
```

you need to implement the function in a test namespace and create the fixture for it:

```clojure
(ns mypapp.core-test
  (:require
      [clj-concordion.core :as conc]))

;; The arguments and return values must by type-hinted using one of the types
;; supported by Concordion: Integer, boolean, or (default) String. If no type 
;; hint is provided at some place, String is assumed.
(defn ^Integer add [^Integer n1, ^Integer n2]
  (int (+ n1 n2)))

;; Create the fixture class and clojure.test test, passing it the function(s)
;; Notice that the name of the class corresponds to the path to the specification
;; .md (plus the optional "Fixture")
;; You can have one or more fixtures in one namespace and the name of the 
;; namespace plays no role
(conc/deffixture "math.AdditionFixture" [add])
```

And run it:

```bash
$ lein with-profile auto test
Compiling mypapp.core-test

lein test mypapp.core-test

file:///var/folders/kg/r_8ytg7x521cvlmz_47t2rgc0000gn/T/concordion/math/Addition.html
Successes: 1, Failures: 0

Ran 1 tests containing 0 assertions.
0 failures, 0 errors.
```

## Gotchas 

1. You need to run `lein clean` if you change/rename the fixture
   so that the class will be recompiled

## Status

Alpha. Core features supported but there are certainly many rough corners and lurking bugs.

## TODO

* The `org.concordion.internal.FixtureRunner.run(org.concordion.api.Fixture)` we
  use is deprecated, we should use `(String example, Fixture fixture)` instead
* Support also ^long, not just int (converting to int underneath)
* In the generated test, add an assertion to check that output from concordion was OK
  so that also clojure.test output shows correctly failed/passed.
* Re-run tests also when the .md files changes - add the resources/ to the watch path

## Implementation

Concordion uses [OGNL](https://commons.apache.org/proper/commons-ognl/) to map function calls 
and property access in the specification to the fixture class, using presumably
reflection, requiring that the fixture class has a method with a corresponding name
and compatible params. 

Unless we decide to replace Concordion's default `Evaluator` with something more
Clojure-friendly, we need to use `gen-class` to create a class and object matching
OGNL's / Concordion's expectations.

The current implementation is based on JUnit3 ConcordionTestCase. 

> There's 2 places where the test runner is called:
  
> Directly when invoking a test - eg. the JUnit4 ConcordionRunner, the JUnit3 ConcordionTestCase. These call FixtureRunner which calls ConcordionBuilder. You're likely to want to create something similar to ConcordionTestCase and FixtureRunner, then reuse ConcordionBuilder - eg, see Mark Derricutt's basic TestNG runner. This was created way back for Concordion 1.3.1 - you'll need to implement the Fixture and FixtureDeclarations interfaces from Concordion 2.0.0 onwards.
>
> Indirectly, from a Concordion Suite, when the concordion:run command is encountered in a specification, spawning a new test within a test. If you want to support the concordion:run command, this is where the Runner / DefaultConcordionRunner comes in. By default ConcordionBuilder plugs in a SystemPropertiesRunnerFactory which lets you override the Runner with a system property. If this doesn't suit, we could open up withRunnerFactory as an extension method - rather than overriding RunStrategy which is designed to cater for different strategies for invoking the Runner.

## License

[Unlicense](https://choosealicense.com/licenses/unlicense/)