Expressions language
====================

Basics
------

### Function calls

We can call the function `hello` and verify that it returns "[hello](- "?=hello()")".

(Notice that a function name is a Clojure symbol and you can use any characters it allows (except `=`) though we
generally follow Concordion in using only alphanumeric names in camelCase.)

#### Limitations

* No nested calls
* The function must be defined in the same namespace as the corresponding `deffixture`

### Variables

We can set a variable to "[Hi!](- "#greeting")" and verify  that it contains "[Hi!](- "?=#greeting")".

We can also [store the result](- "#fnRes=toString('verify the variable')") of function call into a variable
and [verify the variable](- "?=#fnRes").

Variables can also contain data, namely **maps** and we can access their parts using custom functions or
Clojure's `get` and `get-in`. So, having a [kid](- "#kid = ->Kid('Bobby')") and 
an [employee](- "#employee = ->Employee('John', 42, #kid)"):

* Use `get` to access the employee's name: [John](- "?=get(#employee :name)")
* Use `get-in` to access the employee's kid's name: [Bobby](- "?=get-in(#employee [:kid :name])")

### Arguments

Having the variable containing "[Ostrava](- "#city")", we can pass the **variable** to a function
and get back its string value ["Ostrava"](- "?=prStr(#city)").

We can also pass in a **literal string** `'literal'` (we need single quotes because it happens
inside a douple-quoted string) and get it back: ["literal"](- "?=prStr('literal')")

We can also pass in a **literal number** such as 9 and get it: [9](- "?=prStr(9)").

We can also pass in a **keyword**: `:kwd` -> [:kwd](- "?=prStr(:kwd)").

We can also pass in a **nil**: `nil` -> [nil](- "?=prStr(nil)").

We can also pass in a **vector** of any of the above: `[1 2]` -> [[1 2]](- "?=prStr([1 2])").

Multiple arguments may be **separated** [by commas](- "#r1 = sum(1, 2, 3)") but [don't need to](- "#r2 = sum(1 2 3)") - you get the [same result](- "c:assertTrue=eq(#r1, #r2)") anyway.

Extra
-----

We can [set a variable to nil](- "#nilVar = getNil()") and verify we can read that it is 
[nil](- "?=prStr(#nilVar)"). (I.e. nil values are legal in variables.)