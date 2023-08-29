package name.heavycarbon.h2_exercises.transactions.sql_timeout;

import name.heavycarbon.h2_exercises.transactions.agent.Agent;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainer;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AgentContainer_SqlTimeout extends AgentContainer {

    private final AgentId alfaId = new AgentId("alfa");
    private final AgentId bravoId = new AgentId("bravo");
    private final AppState appState = new AppState();

    public AgentContainer_SqlTimeout(
            @NotNull Db db,
            @NotNull Isol isol,
            @NotNull Setup setup,
            @NotNull TransactionalGateway txGw) {
        final var alfa = new Runnable_Alfa(db, appState, alfaId, isol, setup, txGw);
        final var bravo = new Runnable_Bravo(db, appState, bravoId, isol, setup, txGw);
        setUnmodifiableAgentMap(List.of(new Agent(alfa), new Agent(bravo)));
    }

    public Runnable_Alfa getAlfa() {
        return (Runnable_Alfa) (super.get(alfaId).getRunnable());
    }

    public Runnable_Bravo getBravo() {
        return (Runnable_Bravo) (super.get(bravoId).getRunnable());
    }
}