# clj-concordion

Developer-friendly, simple BDD tests using Clojure and `clojure.test`, based on [Concordion](https://concordion.org/).

With Concordion, you can write high-level description of features in Markdown
and document them with data examples. Hidden instrumentation (in the form of magic links)
binds the examples to functions in your Fixture classes (or rather Clojure test namespaces,
in the case of clj-concordion) making it possible to verify
them against the code. When you run your test runner, Concordion also generates
HTML files incorporating results of the passed or failed examples/tests, which you
can publish for the business audience of your code.

![](https://concordion.org/img/how-it-works-markdown.png)

Concordion is very simple and is quite limited at what you can do in the .md files. That is IMO a very good thing. All the logic should be in code, the specifications only supply inputs, outputs, and simple test predicates.

I will not explain why to write Specification By Example (~ Behavior-Driven Development, BDD)
tests and how to do that because the [Concordion site](https://concordion.org/) does a great
job of that. I highly recommend that you check it out first. If you want to see how it works in Clojure, have a look at the [Coding section](#coding) below.

## Usage

Docs: [![cljdoc badge](https://cljdoc.org/badge/clj-concordion/clj-concordion)](https://cljdoc.org/d/clj-concordion/clj-concordion/CURRENT)

TIP: clj-concordion exports [clj-kondo config for you to import](https://github.com/clj-kondo/clj-kondo/blob/master/doc/config.md#exporting-and-importing-configuration) so that Kondo understands its macro.

### Preparation

Add a dependency on this project (copy the [latest dependency specification for Lein/Boot/deps.edn/Gradle/Maven from Clojars](https://clojars.org/clj-concordion)):

[![Clojars Project](https://img.shields.io/clojars/v/clj-concordion.svg)](https://clojars.org/clj-concordion)

(Depending on your build tool, you might also need to add an explicit dependency on `org.concordion/concordion`, [see our project.clj](https://github.com/holyjak/clj-concordion/blob/master/project.clj).)

### Coding

We start by writing a Concordion specification, such as `<class path>/math/Addition.md` containing the specification of a feature, with examples:

```markdown
# Addition

Adding numbers follows the rules of math.

### Examples

Adding [1](- "#n1") and [3](- "#n2") yields [4](- "?=add(#n1, #n2)").
```

The last line would render as 

> Adding [1](- "#n1") and [3](- "#n2") yields [4](- "?=add(#n1, #n2)").

or rather, with indication of the success:

> Adding [1](- "#n1") and [3](- "#n2") yields <span style="background-color: #afa">4</span>.

NOTE: The [Concordion Instrumenting docs](https://concordion.org/instrumenting/java/markdown/) explain how these "magical" links work so only a brief summary: `1` and `3` are stored into the "variables" `#n1` and `#n2` and then we verify that `4` equals to the result of calling the function `add` with these arguments. 

So you need to implement the function `add` in a test namespace with a clj-concordion _fixture_. The name of the ns needs to match the directory of the .md specification + `-test` (here: `math/` -> `math-test`) and it needs to `(deffixture <name of the md file>)`, as we see below:

```clojure
(ns math-test
  (:require
      [clojure.test :refer :all]
      [clj-concordion.core :as cc]))

; The arguments are always *Strings*
(defn add [n1 n2]
  (int (+ (Integer/parseInt n1) (Integer/parseInt n2))))

;; Create the fixture class and clojure.test test.
;; Notice that the name of the ns and fixture corresponds to the path to the specification
;; .md (excluding the "-test" suffix of the ns)
;; We could define multiple fixtures here, if we have more .md files under math/.
;; (so make sure they don't use the same fn name + arity without being happy to
;; share the same implementation)
(cc/deffixture Addition)

;; Ensure Concordion is reset between each run (when running repeatedly via REPL)
(use-fixtures :once cc/cljtest-reset-concordion)
```

(You can explore clj-concordion's own [.md specs](./test-resources)
and the corresponding [fixture code](./test).)

Now run the tests:

```bash
$ lein with-profile auto test
lein test math-test

file:///var/folders/kg/r_8ytg7x521cvlmz_47t2rgc0000gn/T/concordion/math/Addition.html
Successes: 1, Failures: 0

Ran 1 tests containing 0 assertions.
0 failures, 0 errors.
```

You can open the .html file to see the .md file rendered with results of the tests.

#### Deviation from Concordion

* See "Valid expressions in specification files" below
* The option `declaresFullOGNL` is not supported because we have our own evaluator.

##### Valid expressions in specification files

We use our own expression evaluator instead of Concordion's OGNL one, which has
some consequences, both positive and negative.

The expressions are a subset & superset of EDN and thus:

* Constants are supported. Ex.: `[ ](- "myFn(#var1, 'literal string', 123)")`
* Keywords are allowed: `"doSomething(:action 'fire!')"`
* Commas are optional
* Spaces between elements may be _necessary_ (since `=` is a valid part of name in Clojure but in
  an expression we most likely want to break around it)
* Special handling of `'` and `#`: all `'` are replaced with `"` and all `#` are removed.
  This can conflict with test values that contain them - report an issue if it happens.

See the [Expression specification](/test-resources/clj-concordion/expressions/Expressions.md) for details.

#### Options

Notice that `deffixture` takes a second, optional parameter, a map of options - [see the `:cc/opts` Clojure spec for valid keys and values](/src/clj_concordion/specs.clj) and
Concordion [Fixture classes docs](https://concordion.org/coding/java/markdown/#fixture-classes) (->
[FixtureDeclarations.java](https://github.com/concordion/concordion/blob/2.2.0/src/main/java/org/concordion/api/FixtureDeclarations.java),
 [ConcordionOptions.java](https://github.com/concordion/concordion/blob/2.2.0/src/main/java/org/concordion/api/option/ConcordionOptions.java),
 [ConcordionOptions spec](https://concordion.github.io/concordion/latest/spec/annotation/ConcordionOptions.html)) and below for their meaning.
 There is also an extensive [example in the `Addition deffixture`](https://github.com/holyjak/clj-concordion/blob/master/test/math/algebra_test.clj)

The options map replaces Concordion annotations on test classes (e.g. using `:concordion/impl-status :unimplemented` instead of `@Unimplemented`,
an individual `:concordion.option/<option-name>` instead of `@ConcordionOptions(<optionName>=..)`, `@FailFast(onExceptionType={DatabaseUnavailableException.class})` ->
`:concordion/fail-fast-exceptions [Throwable]`) etc.), annotations on test methods such as [Before and After Hooks][before-after-hooks], and exposes additional configuration (see below).

[before-after-hooks]: https://concordion.github.io/concordion/latest/spec/annotation/BeforeAndAfterMethodHooks.html


##### Unsupported Concordion options

* There is yet no support for [adding resources ~ `@ConcordionResources`](https://concordion.org/coding/java/markdown/#adding-resources) because I haven't figured out how to enable doing it for all / subset of fixtures instead of just a single fixture. Suggestions welcome!
* ` @FullOGNL` because we use our own expression implementation instead of OGNL (and it provides ± the same power, if not more)
* [Adding Extensions with `@org.concordion.api.extension.Extensions`](https://concordion.github.io/concordion/latest/spec/common/extension/ExtensionConfiguration.html) - you can do this instead by setting the system property `concordion.extensions`

##### clj-concordion specific options

* `:cc/no-asserts?` - if `true` do not log a warning when the specification has no asserts (i.e. `?=...`, `c:assertTrue=...` etc).
* `:cc/no-trim?` - if `true` do not `trim` variable values (which we do because Concordion includes an extraneous whitespace in table-initialized variables)
* `:cc/(before|after)-*` - see below

##### Setup & tear-down functions

The `opts` argument to `deffixture` can also contain [setup/tear-down functions][before-after-hooks] run at different points of the lifecycle:

```clojure
(cc/deffixture Addition
  {:cc/before-suite   #(println "AdditionFixture: I run before each Suite")
   :cc/before-spec    #(println "AdditionFixture: I run before each Spec")
   :cc/before-example (fn [exname] (println "AdditionFixture: I run before example" exname))
   :cc/after-example  (fn [exname] (println "AdditionFixture: I run after example" exname))
   :cc/after-spec     #(println "AdditionFixture: I run after each Spec")
   :cc/after-suite    #(println "AdditionFixture: I run after each Suite")})
```

##### Troubleshooting: Fail fast upon an exception or a failure

You can instruct clj-concordion to stop at once when a test fails or throws an
exception so that you can examine the runtime state. Use the following options:

* `:concordion/fail-fast true` - stop on the first failure or exception
* `:concordion/fail-fast :failures` - stop on the first failure
* `:concordion/fail-fast :exceptions`  - stop on the first exception; same as `:concordion/fail-fast-exceptions #{Throwable}`
* `:concordion/fail-fast-exceptions #{my.app.MyBizException, my.app.AnotherException}` - stop on the first exception of a matching type; can be combined with
   `:concordion/fail-fast true` to also stop on test failures

(Notice that it is only meaningful to include both options if you use `fail-fast-exceptions` to limit to subclass(es)
and `fail-fast true` so that it also stops for test failures.)

**BEWARE** The "fail fast" applies only to a single fixture / specification. If you want to make sure that only a single
fixture runs then use `test-fixture` to run only it:

```clojure
(ns my.xy-test (:require [clj-concordion.core :as cc]))
(cc/deffixture Addition {:concordion/fail-fast true})
(cc/test-fixture Addition) ;; normally you'd run this from the REPL...
```

#### REPL development

To be able to run tests repeatedly from the REPL, you need to reset the previously cached results:

```clojure
(do
  (cc/reset-concordion!)
  (run-tests))
```

### Additional resources

* [Concordion: Markdown Grammar](https://concordion.github.io/concordion/latest/spec/specificationType/markdown/Markdown.html)

## Troubleshooting

### General

When troubleshooting, [enable debug logging](https://github.com/clojure/tools.logging/blob/master/README.md) for
 namespaces `clj-concordion.*`.

### Common problems

#### Warning: The specification  with the fixture <spec> seems to have no asserts

This warning is logged when the result from Concordion has zero all of the success, failure, and exception counts.
It is OK to ignore if your `.md` file has indeed no asserts and you can disable it by setting the options
`{:cc/no-asserts? true}` on the `deffixture`. But if the spec has asserts and you expected to see some results then
something went wrong. Enable debug logging as described above, check carefully the output (also the terminal if you connect to a remote REPL),
try to debug to find out what is Concordion doing.

## Status

Stable. We expect small releases with bug fixes and occasional additional functionality as requested by the library users.

## Changelog

[See CHANGELOG.md](./CHANGELOG.md).

## TODO

* Re-run tests also when the .md files changes - add the resources/ to the watch path

## Implementation

NOTE: Concordion normally uses [OGNL](https://commons.apache.org/proper/commons-ognl/) to map function calls
and property access in the specification to the fixture class. We replace it with
our own evaulator so that we don't need to generate classes from Clojure.

How is a specification test invoked:

> There's 2 places where the test runner is called:

> Directly when invoking a test - eg. the JUnit4 ConcordionRunner, the JUnit3 ConcordionTestCase. These call FixtureRunner which calls ConcordionBuilder. You're likely to want to create something similar to ConcordionTestCase and FixtureRunner, then reuse ConcordionBuilder - eg, see Mark Derricutt's basic TestNG runner. This was created way back for Concordion 1.3.1 - you'll need to implement the Fixture and FixtureDeclarations interfaces from Concordion 2.0.0 onwards.
>
> Indirectly, from a Concordion Suite, when the concordion:run command is encountered in a specification, spawning a new test within a test. If you want to support the concordion:run command, this is where the Runner / DefaultConcordionRunner comes in. By default ConcordionBuilder plugs in a SystemPropertiesRunnerFactory which lets you override the Runner with a system property. If this doesn't suit, we could open up withRunnerFactory as an extension method - rather than overriding RunStrategy which is designed to cater for different strategies for invoking the Runner.

## Development

### Testing

```
lein with-profile test auto test
```

### Deployment

```
# ! Update CHANGELOG.md !
# Ensure credentials unlocked
gpg --quiet --batch --decrypt ~/.lein/credentials.clj.gpg
# Deploy
lein deploy clojars

# Likely: tag, change version to <next>-SNAPSHOT
git tag <version>; git push; git push --tags
```

## License

[Unlicense](https://choosealicense.com/licenses/unlicense/)
