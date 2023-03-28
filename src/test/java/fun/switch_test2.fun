

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
        case false:
            v = 99
        .
        default:
           v = 8
       .
    .

   write(v)
.
