
proc main ():
    int n = 1
    int v = 0
    bool c =  true
    switch n:
        case 1:
            switch c:
                case false:
                    write(0)
                    v = 3
                .
                default:
                    write(1)
                    v = 7
                .
            .
        .
        default:
           v = 8
       .
    .
   write(v)
.
