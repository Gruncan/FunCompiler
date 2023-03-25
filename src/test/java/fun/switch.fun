

proc main ():
    int n = 9
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
