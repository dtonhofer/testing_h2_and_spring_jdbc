package name.heavycarbon.h2_exercises.storing_instants;

// ---
// This is a thin wrapper around int, giving us an int with a specific type.
// ---

import org.jetbrains.annotations.NotNull;

public final class InstantId implements Comparable<InstantId> {

    private final int id;

    public InstantId(int id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        return (o instanceof InstantId && id == ((InstantId) o).id);
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return "InstantId-" + id;
    }

    public int getRaw() {
        return id;
    }

    @Override
    public int compareTo(@NotNull InstantId o) {
        return this.id - o.id;
    }
}