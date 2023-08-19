package name.heavycarbon.h2_exercises.transactions.nonserializable;

import name.heavycarbon.h2_exercises.transactions.agent.Agent;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainerBase;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.session.Isol;
import name.heavycarbon.h2_exercises.transactions.nonserializable.ReadWriteRunnable.LeftAndRightStuffId;
import name.heavycarbon.h2_exercises.transactions.nonserializable.ReadWriteRunnable.Role2Behaviour;
import name.heavycarbon.h2_exercises.transactions.nonserializable.ReadWriteRunnable.Role;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class AgentContainer_NonSerializable extends AgentContainerBase {

    public final AgentId agentId1 = new AgentId("1");
    public final AgentId agentId2 = new AgentId("2");
    public final AppState appState = new AppState();

    public AgentContainer_NonSerializable(
            @NotNull Db db,
            @NotNull Isol isol,
            @NotNull ReadWriteTransactional tx,
            @NotNull LeftAndRightStuffId ids,
            @NotNull Role2Behaviour r2b) {
        final Map<AgentId, Agent> agentMap = new HashMap<>();;
        final var runnable1 = new ReadWriteRunnable(db, appState, agentId1, isol, Role.Role1, tx, ids, null);
        final var runnable2 = new ReadWriteRunnable(db, appState, agentId2, isol, Role.Role2, tx, ids, r2b);
        final var thread1 = new Thread(runnable1);
        final var thread2 = new Thread(runnable2);
        agentMap.put(agentId1, new Agent(agentId1, thread1, runnable1));
        agentMap.put(agentId2, new Agent(agentId2, thread2, runnable2));
        agentMap.values().forEach(agent -> {
            agent.thread().setDaemon(true);
            agent.thread().setName(agent.agentId().toString());
            agent.runnable().setAnyThreadTerminatedBadly(this::isAnyThreadTerminatedBadly);
        });
        setUnmodifiableAgentMap(agentMap);
    }

    public @NotNull ReadWriteRunnable getRunnable(AgentId id) {
        return (ReadWriteRunnable) (get(id).runnable());
    }

}