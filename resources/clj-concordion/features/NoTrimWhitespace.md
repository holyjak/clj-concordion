Don't trim whitespace when requested
====================================

via the `:cc/no-trim?` option.

So having the table

| [ ][act][Value][val] |
|----------------------|
| followed by space    |


[val]: - "#val"
[act]: - "#result = return(#val)"

We expect to get "[followed by space ](- "?=#result")" when we process the column's value.