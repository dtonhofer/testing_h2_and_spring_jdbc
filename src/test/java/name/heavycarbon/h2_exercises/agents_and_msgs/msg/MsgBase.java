package name.heavycarbon.h2_exercises.agents_and_msgs.msg;

import name.heavycarbon.h2_exercises.agents_and_msgs.agent.AgentId;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

// ---
// Abstract base class for TrueMsg and AckMsg
// ---

public abstract class MsgBase {

    final @NotNull MsgId id;
    final @NotNull MsgState msgState;
    final @NotNull AgentId sender;
    final @NotNull AgentId receiver;

    public MsgBase(@NotNull MsgId id, @NotNull MsgState msgState, @NotNull AgentId sender, @NotNull AgentId receiver) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(msgState, "state");
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(receiver, "receiver");
        this.id = id;
        this.msgState = msgState;
        this.sender = sender;
        this.receiver = receiver;
    }

    public @NotNull MsgId getId() {
        return id;
    }

    public @NotNull MsgState getState() {
        return msgState;
    }

    public @NotNull AgentId getSender() {
        return sender;
    }

    public @NotNull AgentId getReceiver() {
        return receiver;
    }
}
