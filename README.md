# FunCompiler

An extension to the UofG compiler for the fun programming language written in java.

Built using the [ANTLR](https://www.antlr.org/) framework


## Switch statement example
```fun

proc main ():
    bool n = true
    int v = 0
    switch n:
        case 5..9:
            v = v + 1
            write(n)
            write(5)
        .
        case 122:
            v = 68
        .
        default:
           v = 8
       .
    .

   write(v)
.
```

## Repeat until example

```fun

proc main ():
    int v = 0
    repeat-until v - 100 > 0:
        v =  (v + 10) * 2
        write(v)
    .
.
```
