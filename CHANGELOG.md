# Changelog

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
