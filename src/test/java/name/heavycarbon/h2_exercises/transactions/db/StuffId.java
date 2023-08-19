package name.heavycarbon.h2_exercises.transactions.db;

import org.jetbrains.annotations.NotNull;

// ---
// This is wrapper around int, basically giving us an int with a specific type.
// ---

public final class StuffId implements Comparable<StuffId> {

    private final int id;

    public StuffId(int id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        return (o instanceof StuffId && id == ((StuffId) o).id);
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return "StuffId-" + id;
    }

    public int getRaw() {
        return id;
    }

    @Override
    public int compareTo(@NotNull StuffId o) {
        return this.id - o.id;
    }
}
