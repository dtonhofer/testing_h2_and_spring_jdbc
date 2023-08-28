package name.heavycarbon.h2_exercises.transactions.db;

// ---
// This is wrapper around int, giving us an int with a specific type.
// ---

public final class EnsembleId {

    public final static EnsembleId One = new EnsembleId(1);
    public final static EnsembleId Two = new EnsembleId(2);

    private final int id;

    public EnsembleId(int id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        return (o instanceof EnsembleId && id == ((EnsembleId) o).id);
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return "EnsembleId-" + id;
    }

    public int getRaw() {
        return id;
    }
}