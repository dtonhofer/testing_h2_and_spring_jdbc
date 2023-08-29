package name.heavycarbon.h2_exercises.transactions.phantom_read;

import name.heavycarbon.h2_exercises.transactions.agent.Agent;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainer;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AgentContainer_PhantomRead extends AgentContainer {

    private final AgentId modifierId = new AgentId("modifier");
    private final AgentId readerId = new AgentId("reader");
    private final AppState appState = new AppState();

    public enum PhantomicPredicate {Unset, ByEnsembleAndPayload, ByEnsemble, ByPayload}

    public AgentContainer_PhantomRead(
            @NotNull Db db,
            @NotNull Isol isol,
            @NotNull Op op,
            @NotNull PhantomicPredicate phantomicPredicate,
            @NotNull Setup setup,
            @NotNull TransactionalGateway txGw) {
        final var mr = new AgentRunnable_PhantomRead_Modifier(db, appState, modifierId, isol, op, setup, txGw);
        final var rr = new AgentRunnable_PhantomRead_Reader(db, appState, readerId, isol, op, phantomicPredicate, setup, txGw);
        setUnmodifiableAgentMap(List.of(new Agent(mr), new Agent(rr)));
    }

    public @NotNull AgentRunnable_PhantomRead_Reader getReaderRunnable() {
        // Too much effort to make the get typesafe, just cast!
        return (AgentRunnable_PhantomRead_Reader) (get(readerId).getRunnable());
    }

}
