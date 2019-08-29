# Changelog

* 1.0.1
  * Less noisy output - no more ConcordionAssertionError stacktraces
  * Nicer logging of example names in a spec
* 1.0.0 Rebranding of version 0.3.0
* 0.3.0
  * Automatically trim variable values - useful because Concordion 2.2.0 somehow includes an extra space in values of variables initialized from a table; prevent with `:cc/no-trim?`
  * Add the option `:cc/no-asserts?` - to prevent warning when the specification has no asserts
* 0.2.0
  * Allow use of `nil` and vectors inside Concordion expressions
  * Expose `clojure.core`'s `get` and `get-in` so that they can be used
    in expressions
  * Add [Expression documentation](clj-concordion/expressions/Expressions.md)
  * Fix: variables may now have `nil` value (e.g. when returned from a function call)
* 0.1.1
  * Don't swallow errors in `beforeExample`
* 0.0.10
  * [breaking] Simplified, got rid of `gen-class`. Breaking: `deffixture` no longer takes a list of functions. We have now our own evaluator but it should be compatible with Concordion expressions. Arguments can be variables or constants.
* 0.0.5
  * [feature] Support the example command <=> use JUnit4-style integration
  * [breaking] change: qualified keys that used `:clj-concordion.core/` now use `:cc/`. 
* 0.0.4 
  * [feature] Add `(reset-concordion!)` so that tests can be run repeatedly from a REPL
  * [feature] Better error reporting when feature classes not found
* 0.0.3 [feature] Support fixture options and `before*/after*` functions
* 0.0.2
  * [feature] Support `concordion:run` command 
  * [fix] Enable multiple `deffixture` in the same file
* 0.0.1 Initial release with basic `deffixture`
