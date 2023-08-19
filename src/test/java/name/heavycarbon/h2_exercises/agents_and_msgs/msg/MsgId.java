package name.heavycarbon.h2_exercises.agents_and_msgs.msg;

import org.jetbrains.annotations.NotNull;

// ---
// This is wrapper around int, basically giving us an int with a specific type.
// ---

public final class MsgId implements Comparable<MsgId>{

    private final int id;

    public MsgId(int id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        return (o instanceof MsgId && id == ((MsgId) o).id);
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public @NotNull String toString() {
        return "MsgId-" + id;
    }

    public int getRaw() {
        return id;
    }

    @Override
    public int compareTo(@NotNull MsgId o) {
        return this.id - o.id;
    }
}