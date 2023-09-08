package name.heavycarbon.h2_exercises.transactions.agent;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ---
// Base class for "containers of agents"
// ---

@Slf4j
public abstract class AgentContainer {

    // This map is immutable

    private Map<AgentId, Agent> agentMap;

    // fire-once setter for the agentMap, set it to an unmodifiable map

    protected void setUnmodifiableAgentMap(List<Agent> agents) {
        Map<AgentId, Agent> agentMapTmp = new HashMap<>();
        for (Agent agent : agents) {
            agentMapTmp.put(agent.getAgentId(), agent);
            agent.getThread().setDaemon(true);
            agent.getThread().setName(agent.getAgentId().toString());
            agent.getRunnable().setAnyAgentTerminatedBadly(this::isAnyAgentTerminatedBadly);
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
    //
    // Values of "agent state" from AgentRunnable:
    //
    // 0: Agent's thread not yet started (Thread.isAlive() probably returns false)
    // 1: Agent's thread has been started (Thread.isAlive() returns true)
    // 1: Agent's thread terminated unexpectedly (Thread.isAlive() returns false, again but the value is still 1)
    // 2: Agent's thread terminated nicely/normally (Thread.isAlive() probably returns false)

    public boolean isAnyAgentTerminatedBadly() {
        for (Agent agent : agentMap.values()) {
            final int agentState = agent.getRunnable().getAgentState();
            if (agentState == 0) {
                // Agent's thread not yet started (Thread.isAlive() probably returns false)
                // Looks good!
            } else if (agentState == 2) {
                // Agent's thread terminated nicely/normally (Thread.isAlive() probably returns false)
                // Looks good!
            } else if (agentState == 1) {
                // Agent's thread has been started...
                // but if Thread.isAlive() returns false, then it terminated unexpectedly
                if (agent.getThread().isAlive()) {
                    // Looks good!
                } else {
                    // At least one thread terminated badly
                    return true;
                }
            } else {
                throw new IllegalStateException("Unknown thread state");
            }
        }
        // none "terminated badly"
        return false;
    }

    // It makes no sense to synchronize this; the result depends on the momentary thread state.

    public boolean isAllAgentsTerminated() {
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
