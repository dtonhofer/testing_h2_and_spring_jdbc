package name.heavycarbon.h2_exercises.agents_and_msgs.msg;

import name.heavycarbon.h2_exercises.agents_and_msgs.agent.AgentId;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class TrueMsg extends MsgBase {

    private final @NotNull String text;

    public TrueMsg(@NotNull MsgId id, @NotNull MsgState msgState, @NotNull AgentId sender, @NotNull AgentId receiver, @NotNull String text) {
        super(id, msgState, sender, receiver);
        Objects.requireNonNull(text, "text");
        this.text = text;
    }

    public @NotNull String getText() {
        return text;
    }
}
