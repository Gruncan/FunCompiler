
proc main ():
    int n = 98
    int v = 0
    bool c =  true
    switch n:
        case 0..90:
            v = 99
        .
        case 98:
        .
        case 102:
            write(100 / 5  + 7)
        .
        case 105..108:
            write(105)
            write(106)
            write(107)
            write(108)
        .
        default:
           v = 8
       .
    .
   write(v)
.
