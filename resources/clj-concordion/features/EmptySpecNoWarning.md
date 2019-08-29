Empty spec with no asserts
==========================

When a spec has no asserts (i.e. Concordion returns 0 success/failure/exception counts), `clj-concordion`
prints the warning

> Warning: The specification  with the fixture $fixture$ seems to have no asserts.

It is possible to suppress this warning for specs that are intended to have no asserts
(because we only include them so that the corresponding .md gets rendered into a .html)
by setting the `:cc/no-asserts?` option.

(Check the REPL/terminal to see that there is no warning.)