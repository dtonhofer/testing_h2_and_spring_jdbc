package name.heavycarbon.h2_exercises.transactions.agent;

import org.jetbrains.annotations.NotNull;

// ---
// This is wrapper around String, basically giving us a String with a specific type.
// ---

public final class AgentId implements Comparable<AgentId> {

    private final String id;

    public AgentId(@NotNull String id) {
        this.id = id.trim();
        assert !this.id.equals("");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        return (o instanceof AgentId && id.equals(((AgentId) o).id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "AgentId-" + id;
    }

    public String getRaw() {
        return id;
    }

    @Override
    public int compareTo(@NotNull AgentId o) {
        return this.id.compareTo(o.id);
    }
}
