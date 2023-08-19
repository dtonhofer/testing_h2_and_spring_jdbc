package name.heavycarbon.h2_exercises.transactions.agent;

// ---
// Base class for "containers of agents"
// ---

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

public abstract class AgentContainerBase {

    // a test may select on of the following operations

    public enum Op {Insert, Update, Delete}

    private Map<AgentId, Agent> agentMap;

    // fire-once setter for the agentMap, set it to an unmodifiable map

    protected void setUnmodifiableAgentMap(@NotNull Map<AgentId, Agent> agentMap) {
        if (this.agentMap != null) {
            throw new IllegalStateException("AgentMap has already been set");
        }
        this.agentMap = Collections.unmodifiableMap(agentMap);
    }

    protected @NotNull Map<AgentId, Agent> getAgentMap() {
        if (agentMap == null) {
            throw new IllegalStateException("AgentMap is currently (null)");
        }
        return agentMap;
    }

    // This does not need to be synchronized.
    // Will return null if there is no such agent.
    // TODO: Should return Optional

    public Agent get(@NotNull AgentId agentId) {
        return agentMap.get(agentId);
    }

    // It makes no sense to synchronize this; the result depends on the momentary thread state.
    // This is called repeatedly from the individual threads to check whether everything is
    // still fine or whether they should exit early

    public boolean isAnyThreadTerminatedBadly() {
        return agentMap.values().stream().anyMatch(
                agent -> !agent.thread().isAlive() && !agent.runnable().isTerminatedNicely());
    }

    // It makes no sense to synchronize this; the result depends on the momentary thread state.

    public boolean isAllThreadsTerminated() {
        return agentMap.values().stream().noneMatch(agent -> agent.thread().isAlive());
    }

    // Called from main thread

    public void startAll() {
        agentMap.values().forEach(agent -> agent.thread().start());
    }

    // Called from main thread
    // TODO: If the agent threads deadlock or livelock, this method will wait forever.
    // TODO: It should issue an across-the board interrupt instead.
    // TODO: Or just terminate, the agent threads are daemons after all.

    public void joinAll() {
        // a loop so that we can break
        for (Agent agent : agentMap.values()) {
            try {
                agent.thread().join();
            } catch (InterruptedException ex) {
                // Who the hell interrupted us? the user??
                // In any case, do *not* join any remaining threads!
                break;
            }
        }
    }

}
