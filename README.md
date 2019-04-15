# clj-concordion

Proof of Concept of writing Specification By Example tests using
[Concordion](https://concordion.org/) and implementing them using Clojure and `clojure.test`.

With Concordion, you can write high-level specification of features in Markdown
and document them with examples. Hidden instrumentation (in the form of magic links)
binds the examples to functions in your Fixture classes making it possible to run
the tests. When you run your tests, Concordion does generate HTML files incorporating 
results of the passed or failed examples/tests.

![](https://concordion.org/img/how-it-works-markdown.png)

## Usage

`lein with-profile auto test`

## Gotchas 

1. You need to run `lein clean` if you change/rename the generated class 
 (lein doesn't detect it and won't compile it again)

## Status

Work in progress. Currently we have a spec and a corresponding fixture created in a Clojure namespace and can run it with `lein test`. But the integration is very manual now.

## Battle plan

We want to be able to do something like

```clojure
;; Example: Adding [1](- "#n1") and [3](- "#n2") yields [4](- "?=add(#n1, #n2)").
(deffixture 
  (add [n1 n2]
    (+ n1 n2)))
```

which would translate into

```clojure
(gen-class
  :name "<ns>.Fixture"
  :methods [[add [Integer Integer] Integer]])

(defn -add [_ n1 n2]
  (int (+ n1 n2)))


(deftest concordion
  (run-via-concordion (<ns>.Fixture.)))
  
;; In a support library:
(defn run-via-concordion [fixture]
    (let [fixture-meta (doto (FixtureInstance. fixture)
                         (.beforeSpecification)
                         (.setupForRun fixture))]
        (.run
          (FixtureRunner. fixture-meta (ClassNameBasedSpecificationLocator.))
          fixture-meta)
        (.afterSpecification fixture-meta)))
```

### TODO

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
`concordion:run` isn't supported yet.

> There's 2 places where the test runner is called:
  
> Directly when invoking a test - eg. the JUnit4 ConcordionRunner, the JUnit3 ConcordionTestCase. These call FixtureRunner which calls ConcordionBuilder. You're likely to want to create something similar to ConcordionTestCase and FixtureRunner, then reuse ConcordionBuilder - eg, see Mark Derricutt's basic TestNG runner. This was created way back for Concordion 1.3.1 - you'll need to implement the Fixture and FixtureDeclarations interfaces from Concordion 2.0.0 onwards.
>
> Indirectly, from a Concordion Suite, when the concordion:run command is encountered in a specification, spawning a new test within a test. If you want to support the concordion:run command, this is where the Runner / DefaultConcordionRunner comes in. By default ConcordionBuilder plugs in a SystemPropertiesRunnerFactory which lets you override the Runner with a system property. If this doesn't suit, we could open up withRunnerFactory as an extension method - rather than overriding RunStrategy which is designed to cater for different strategies for invoking the Runner.

## License

[Unlicense](https://choosealicense.com/licenses/unlicense/)