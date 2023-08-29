package name.heavycarbon.h2_exercises.transactions.deadlock;

import name.heavycarbon.h2_exercises.transactions.agent.Agent;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainer;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AgentContainer_Deadlock extends AgentContainer {

    private final AgentId alfaId = new AgentId("alfa");
    private final AgentId bravoId = new AgentId("bravo");
    private final AppState appState = new AppState();

    public AgentContainer_Deadlock(
            @NotNull Db db,
            @NotNull Isol isol,
            @NotNull Setup setup,
            @NotNull TransactionalGateway txGw) {
        final var alfa = new AgentRunnable_Alfa(db, appState, alfaId, isol, setup, txGw);
        final var bravo = new AgentRunnable_Bravo(db, appState, bravoId, isol, setup, txGw);
        setUnmodifiableAgentMap(List.of(new Agent(alfa), new Agent(bravo)));
    }

    public AgentRunnable_Alfa getAlfa() {
        return (AgentRunnable_Alfa) (super.get(alfaId).getRunnable());
    }

    public AgentRunnable_Bravo getBravo() {
        return (AgentRunnable_Bravo) (super.get(bravoId).getRunnable());
    }

}
