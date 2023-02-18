package fun.types;

import fun.Type;

public class Primitive extends Type {

    public int which;

    public Primitive(int w) {
        this.which = w;
    }

    @Override
    public boolean equiv(Type that) {
        return (that instanceof Primitive && this.which == ((Primitive) that).which);
    }

    @Override
    public String toString() {
        return switch (which) {
            case 0 -> "void";
            case 1 -> "bool";
            case 2 -> "int";
            default -> "???";
        };
    }

}
