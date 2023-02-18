package fun.types;

import fun.Type;

public class Pair extends Type {

    public Type first, second;

    public Pair(Type fst, Type snd) {
        this.first = fst;
        this.second = snd;
    }

    @Override
    public boolean equiv(Type that) {
        if (that instanceof Pair thatPair)
            return this.first.equiv(thatPair.first) && this.second.equiv(thatPair.second);
        else
            return false;
    }

    @Override
    public String toString() {
        return first + " x " + second;
    }
}
