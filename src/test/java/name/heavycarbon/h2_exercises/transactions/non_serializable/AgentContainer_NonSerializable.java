package name.heavycarbon.h2_exercises.transactions.non_serializable;

import name.heavycarbon.h2_exercises.transactions.agent.Agent;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainerBase;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.common.WhatToRead;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.non_serializable.ReadWriteRunnable.Role;
import name.heavycarbon.h2_exercises.transactions.non_serializable.ReadWriteRunnable.Role2Behaviour;
import name.heavycarbon.h2_exercises.transactions.session.Isol;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class AgentContainer_NonSerializable extends AgentContainerBase {

    public final AgentId agentId1 = new AgentId("agent-1");
    public final AgentId agentId2 = new AgentId("agent-2");
    public final AppState appState = new AppState();

    public AgentContainer_NonSerializable(
            @NotNull Db db,
            @NotNull Isol isol,
            @NotNull ReadWriteTransactional tx,
            @NotNull LeftAndRightStuffId ids,
            @NotNull Role2Behaviour r2b) {
        final var runnable1 = new ReadWriteRunnable(db, appState, agentId1, isol, Role.Role1, tx, ids, Optional.empty());
        final var runnable2 = new ReadWriteRunnable(db, appState, agentId2, isol, Role.Role2, tx, ids, Optional.of(r2b));
        setUnmodifiableAgentMap(new Agent(runnable1), new Agent(runnable2));
    }

    public @NotNull ReadWriteRunnable getRunnable(AgentId id) {
        return (ReadWriteRunnable) (get(id).getRunnable());
    }

}