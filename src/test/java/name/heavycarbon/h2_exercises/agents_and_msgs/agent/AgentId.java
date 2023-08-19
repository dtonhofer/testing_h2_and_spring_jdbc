package name.heavycarbon.h2_exercises.agents_and_msgs.agent;

// ---
// This is wrapper around int, basically giving us an int with a specific type.
// ---

import org.jetbrains.annotations.NotNull;

public final class AgentId implements Comparable<AgentId> {

    private final int id;

    public AgentId(int id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        return (o instanceof AgentId && id == ((AgentId) o).id);
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return "AgentId-" + id;
    }

    public int getRaw() {
        return id;
    }

    @Override
    public int compareTo(@NotNull AgentId o) {
        return this.id - o.id;
    }
}
