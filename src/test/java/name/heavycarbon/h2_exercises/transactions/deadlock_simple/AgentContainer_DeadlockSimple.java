package name.heavycarbon.h2_exercises.transactions.deadlock_simple;

import name.heavycarbon.h2_exercises.transactions.agent.*;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AgentContainer_DeadlockSimple extends AgentContainer {

    private final AgentId alfaId = new AgentId("alfa");
    private final AgentId bravoId = new AgentId("bravo");
    private final AppState appState = new AppState();

    public AgentContainer_DeadlockSimple(
            @NotNull Db db,
            @NotNull TransactionalGateway txGw,
            @NotNull Config config,
            @NotNull DbConfig dbConfig) {
        final var alfa = new AgentRunnable_SubAlfa(db, appState, alfaId, txGw, config, dbConfig);
        final var bravo = new AgentRunnable_Bravo(db, appState, bravoId, txGw, config, dbConfig);
        setUnmodifiableAgentMap(List.of(new Agent(alfa), new Agent(bravo)));
    }

    public AgentRunnable_SubAlfa getAlfa() {
        return (AgentRunnable_SubAlfa) (super.get(alfaId).getRunnable());
    }

    public AgentRunnable_Bravo getBravo() {
        return (AgentRunnable_Bravo) (super.get(bravoId).getRunnable());
    }

}
