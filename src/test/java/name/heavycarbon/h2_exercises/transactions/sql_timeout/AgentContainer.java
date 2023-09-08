package name.heavycarbon.h2_exercises.transactions.sql_timeout;

import name.heavycarbon.h2_exercises.transactions.agent.Agent;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AgentContainer extends name.heavycarbon.h2_exercises.transactions.agent.AgentContainer {

    private final AgentId alfaId = new AgentId("alfa");
    private final AgentId bravoId = new AgentId("bravo");
    private final AppState appState = new AppState();

    public AgentContainer(
            @NotNull Db db,
            @NotNull TransactionalGateway txGw,
            @NotNull Config config,
            @NotNull DbConfig dbConfig) {
        final var alfa = new AgentRunnable_Alfa(db, appState, alfaId, txGw, config, dbConfig);
        final var bravo = new AgentRunnable_Bravo(db, appState, bravoId, txGw, config, dbConfig);
        setUnmodifiableAgentMap(List.of(new Agent(alfa), new Agent(bravo)));
    }

    public AgentRunnable_Alfa getAlfa() {
        return (AgentRunnable_Alfa) (super.get(alfaId).getRunnable());
    }

    public AgentRunnable_Bravo getBravo() {
        return (AgentRunnable_Bravo) (super.get(bravoId).getRunnable());
    }
}