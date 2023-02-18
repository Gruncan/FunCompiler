package fun.types;

import fun.Type;

public class Mapping extends Type {
    public Type domain, range;

    public Mapping(Type d, Type r) {
        this.domain = d;
        this.range = r;
    }

    @Override
    public boolean equiv(Type that) {
        if (that instanceof Mapping thatMapping)
            return this.domain.equiv(thatMapping.domain) && this.range.equiv(thatMapping.range);
        else
            return false;
    }

    @Override
    public String toString() {
        return domain + " -> " + range;
    }
}
