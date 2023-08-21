package name.heavycarbon.h2_exercises.transactions.agent;

import org.jetbrains.annotations.NotNull;

// ---
// A very simple container for information about a given agent
// ---

public record Agent(@NotNull AgentId agentId, @NotNull Thread thread, @NotNull AgentRunnableBase runnable) {

}
