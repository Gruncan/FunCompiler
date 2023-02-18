package fun.types;

import fun.Type;

public class Error extends Type {

    public Error() {
    }

    @Override
    public boolean equiv(Type that) {
        return true;
    }

    @Override
    public String toString() {
        return "error";
    }
}
