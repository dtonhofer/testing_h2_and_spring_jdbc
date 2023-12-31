package name.heavycarbon.h2_exercises.transactions.agent;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

// ---
// A very simple container for information about a given agent
// ---

@Value
public class Agent {

    @NotNull AgentId agentId;
    @NotNull Thread thread;
    @NotNull AgentRunnable runnable;

    public Agent(@NotNull AgentRunnable runnable) {
        this.agentId = runnable.getAgentId();
        this.thread = new Thread(runnable);
        this.runnable = runnable;
    }
}
