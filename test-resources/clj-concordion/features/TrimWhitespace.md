Trim whitespace by default
==========================

As of Concordion 2.2.0, variables set from tables have a trailing space 
(probably because the text is followed by space(s) in the column).
Clj-concordion does automatically remove this extra whitespace.

So having the table

| [ ][act][Value][val] |
|----------------------|
| No space, ma!        |


[val]: - "#val"
[act]: - "#result = return(#val)"

We expect to get "[No space, ma!](- "?=#result")" when we process the column's value.