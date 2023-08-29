package name.heavycarbon.h2_exercises.transactions.dirty_read;

import name.heavycarbon.h2_exercises.transactions.agent.Agent;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainer;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AgentContainer_DirtyRead extends AgentContainer {

    private final AgentId modifierId = new AgentId("modifier");
    private final AgentId readerId = new AgentId("reader");
    private final AppState appState = new AppState();

    public AgentContainer_DirtyRead(
            @NotNull Db db,
            @NotNull Isol isol,
            @NotNull Op op,
            @NotNull Setup setup,
            @NotNull TransactionalGateway txGw) {
        final var mr = new ModifierRunnable_DirtyRead(db, appState, modifierId, isol, op, setup, txGw);
        final var rr = new ReaderRunnable_DirtyRead(db, appState, readerId, isol, op, setup, txGw);
        setUnmodifiableAgentMap(List.of(new Agent(mr), new Agent(rr)));
    }

    public @NotNull ReaderRunnable_DirtyRead getReaderRunnable() {
        // Too much effort to make the get typesafe, just cast!
        return (ReaderRunnable_DirtyRead) (get(readerId).getRunnable());
    }

    public @NotNull ModifierRunnable_DirtyRead getModifierRunnable() {
        // Too much effort to make the get typesafe, just cast!
        return (ModifierRunnable_DirtyRead) (get(modifierId).getRunnable());
    }

}
