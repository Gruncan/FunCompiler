package fun.types;

import fun.Type;

import java.util.List;

public class Sequence extends Type {

    public List<Type> sequence;

    public Sequence(List<Type> seq) {
        this.sequence = seq;
    }

    @Override
    public boolean equiv(Type that) {
        if (that instanceof Sequence) {
            List<Type> thatSequence = ((Sequence) that).sequence;
            if (thatSequence.size() != sequence.size()) return false;
            for (int i = 0; i < thatSequence.size(); i++)
                if (!(thatSequence.get(i).equiv(sequence.get(i))))
                    return false;

            return true;
        } else
            return false;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder("[");
        if (sequence.size() > 0) {
            s.append(sequence.get(0));
            for (int i = 1; i < sequence.size(); i++)
                s.append(",").append(sequence.get(i));
        }
        s.append("]");
        return s.toString();
    }

}
