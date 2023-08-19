package name.heavycarbon.h2_exercises.agents_and_msgs.msg;

import name.heavycarbon.h2_exercises.agents_and_msgs.agent.AgentId;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

// ---
// An AckMsg is sent from an agent B that has just received a TrueMsg from an agent A
// back to Agent A to acknowledge the TrueMsg. It carries the id of the TrueMsg that
// is being acknowledged.
// ---

public class AckMsg extends MsgBase {

    private final @NotNull MsgId trueMsgId;

    public AckMsg(@NotNull MsgId id, @NotNull MsgState msgState, @NotNull AgentId sender, @NotNull AgentId receiver, @NotNull MsgId trueMsgId) {
        super(id, msgState, sender, receiver);
        Objects.requireNonNull(trueMsgId, "ackedId");
        this.trueMsgId = trueMsgId;
    }

    public @NotNull MsgId getTrueMsgId() {
        return trueMsgId;
    }
}
