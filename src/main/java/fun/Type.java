////////////////////////////////////////////////////////////////
//
// Representation of types.
//
// Based on a previous version developed by
// David Watt and Simon Gay (University of Glasgow).
//
////////////////////////////////////////////////////////////////

package fun;

import fun.types.Error;
import fun.types.Primitive;
import fun.types.Sequence;

import java.util.ArrayList;

public abstract class Type {

    // An object of class Type represents a Fun type, which may
    // be a primitive type, a pair type, or a mapping type,

    public static final Primitive
            VOID = new Primitive(0),
            BOOL = new Primitive(1),
            INT = new Primitive(2);

    public static final Error ERROR = new Error();

    public static final Sequence EMPTY = new Sequence(new ArrayList<>());

    // Return true if and only if this type is equivalent
    // to that.
    public abstract boolean equiv(Type that);

    // Return a textual representation of this type.
    public abstract String toString();


}
