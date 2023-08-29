package name.heavycarbon.h2_exercises.transactions.agent;

import org.jetbrains.annotations.NotNull;

import java.util.*;

// ---
// Base class for "containers of agents"
// ---

public abstract class AgentContainer {

    // A test may select on of the following operations
    // "MoveIn" and "MoveOut" are for eliciting phantom reads,
    // where you update a record to move it into or out of the result set.

    public enum Op {Unset, Insert, Update, Delete, UpdateIntoPredicateSet, UpdateOutOfPredicateSet}

    private Map<AgentId, Agent> agentMap;

    // fire-once setter for the agentMap, set it to an unmodifiable map

    protected void setUnmodifiableAgentMap(List<Agent> agents) {
        Map<AgentId, Agent> agentMapTmp = new HashMap<>();
        for (Agent agent : agents) {
            agentMapTmp.put(agent.getAgentId(), agent);
            agent.getThread().setDaemon(true);
            agent.getThread().setName(agent.getAgentId().toString());
            agent.getRunnable().setAnyThreadTerminatedBadly(Optional.of(this::isAnyThreadTerminatedBadly));
        }
        this.agentMap = Collections.unmodifiableMap(agentMapTmp);
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
                agent -> !agent.getThread().isAlive() && !agent.getRunnable().isTerminatedNicely());
    }

    // It makes no sense to synchronize this; the result depends on the momentary thread state.

    public boolean isAllThreadsTerminated() {
        return agentMap.values().stream().noneMatch(agent -> agent.getThread().isAlive());
    }

    // Called from main thread

    public void startAll() {
        agentMap.values().forEach(agent -> agent.getThread().start());
    }

    // Called from main thread
    // TODO: If the agent threads deadlock or livelock, this method will wait forever.
    // TODO: It should issue an across-the board interrupt instead.
    // TODO: Or just terminate, the agent threads are daemons after all.

    public void joinAll() {
        // a loop so that we can break
        for (Agent agent : agentMap.values()) {
            try {
                agent.getThread().join();
            } catch (InterruptedException ex) {
                // Who the hell interrupted us? the user??
                // In any case, do *not* join any remaining threads!
                break;
            }
        }
    }

}
