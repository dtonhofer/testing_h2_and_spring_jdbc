package name.heavycarbon.h2_exercises.agents_and_msgs.agent;

import org.jetbrains.annotations.NotNull;

// ---
// An "Agent" is just a minimal container for the combination of AgentId, the
// thread and the runnable.
// ---

public record Agent(@NotNull AgentId agentId, @NotNull Thread thread, @NotNull AgentRunnable runnable) {
}
