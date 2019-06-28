# Math - algebra - addition

We are able to add numbers correctly.

(_A spec using the `concordion:example` command, i.e. a heading with the Concordion magical link._)

### [Per example setup](- "before")

(*A named example with the magical name "before" will run before each example in the spec.*)

Given the base [0](- "#base"), the following examples should pass. <!-- made up example, we don't really use #base -->

### [Example: addition (positive)](-)
<!-- a named example with an implicit name -->

Adding [1](- "#n1") and [3](- "#n2") yields [4](- "?=add(#n1, #n2)").

### [Example: addition (negative)](- "addition-negative") 
<!-- a named example with an explicit name -->

Adding the positive postive number [3](- "#n1") and the negative number [-4](- "#n2") yields [-1](- "?=add(#n1, #n2)").

### [Example: output variable](-)

[Given 1+2](- "#result = add(&quot;1&quot;, &quot;2&quot;)") we expect the result to be [3](- "?=#result").

### [Example: exception handling](- "exception_throwing c:status=ExpectedToFail")

What happens if the code throws an unexpected exception? This:

It [fails with an exception](- "c:assertTrue=throwErr()")