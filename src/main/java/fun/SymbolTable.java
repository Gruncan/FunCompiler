////////////////////////////////////////////////////////////////
//
// Representation of generic symbol tables.
//
// Based on a previous version developed by
// David Watt and Simon Gay (University of Glasgow).
//
////////////////////////////////////////////////////////////////

package fun;

import java.util.HashMap;
import java.util.Map;

/**
 * Representation of generic symbol tables.
 * Based on a previous version developed by
 * David Watt and Simon Gay (University of Glasgow).
 *
 * @param <T>
 */
public class SymbolTable<T> {

    // An object of class SymbolTable<A> represents a symbol
    // table in which identifiers (strings) are associated
    // with attributes of type A. The symbol table comprises
    // a global part (which is always enabled) and a local
    // part (which may be enabled or disabled). Each part is
    // a set of (String,A) entries in which the strings are
    // unique.
    private final Map<String, T> globals;
    private Map<String, T> locals;

    public SymbolTable() {
        this.globals = new HashMap<>();
        this.locals = null;  // initially disabled
    }

    public boolean put(String id, T attr) {
        // Add (id,attr) to this symbol table, either to the
        // local part (if enabled) or to the global part
        // (otherwise). Return true iff id is unique.
        Map<String, T> scope = (this.locals != null ? this.locals : this.globals);
        if (scope.get(id) == null) {
            scope.put(id, attr);
            return true;
        } else
            return false;
    }

    // My code
    public boolean remove(String id) {
        T r;
        if (this.locals != null && this.locals.get(id) != null)
            r = this.locals.remove(id);
        else
            r = this.globals.remove(id);

        return r != null;
    }

    public T get(String id) {
        // Retrieve the attribute corresponding to id in this
        // symbol table. If id occurs in both local and global
        // parts, prefer the local one. Return the attribute,
        // or null if id is not found.
        if (this.locals != null && this.locals.get(id) != null)
            return this.locals.get(id);
        else
            return this.globals.get(id);
    }

    public T getLocal(String id) {
        // Retrieve the attribute corresponding to id in the
        // local part of this symbol table. Return the attribute,
        // or null if id is not found.
        if (this.locals != null)
            return this.locals.get(id);
        else
            return null;
    }

    public void enterLocalScope() {
        // Enable the local part of this symbol table.
        // Assume that locals == null.
        this.locals = new HashMap<>();
    }

    public void exitLocalScope() {
        // Discard all entries in the local part of this
        // symbol table, and disable the local part.
        this.locals = null;
    }

    @Override
    public String toString() {
        // Return a textual representation of this symbol table.
        String s = "Globals: " + this.globals + "\n";
        if (this.locals != null)
            s += "Locals: " + this.locals + "\n";
        return s;
    }
}
