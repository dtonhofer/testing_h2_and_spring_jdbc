package name.heavycarbon.h2_exercises.transactions.session;

import org.jetbrains.annotations.NotNull;

// ---
// This is wrapper around int, basically giving us an int with a specific type.
// ---

public final class SessionId implements Comparable<SessionId> {

    private final int id;

    public SessionId(int id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        return (o instanceof SessionId && id == ((SessionId) o).id);
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return "SessionId-" + id;
    }

    public int getRaw() {
        return id;
    }

    @Override
    public int compareTo(@NotNull SessionId o) {
        return this.id - o.id;
    }
}
