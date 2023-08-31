package name.heavycarbon.h2_exercises.transactions.deadlock_onlywriting;

import name.heavycarbon.h2_exercises.transactions.agent.*;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AgentContainer_DeadlockOnlyWriting extends AgentContainer {

    private final AgentId alfaId = new AgentId("alfa");
    private final AgentId bravoId = new AgentId("bravo");
    private final AppState appState = new AppState();

    public AgentContainer_DeadlockOnlyWriting(
            @NotNull Db db,
            @NotNull Isol isol,
            @NotNull Setup setup,
            @NotNull PrintException pex,
            @NotNull TransactionalGateway txGw) {
        final var alfa = new AgentRunnable_Alfa(db, appState, alfaId, isol, pex, setup, txGw);
        final var bravo = new AgentRunnable_Bravo(db, appState, bravoId, isol, pex, setup, txGw);
        setUnmodifiableAgentMap(List.of(new Agent(alfa), new Agent(bravo)));
    }

    public AgentRunnable_Alfa getAlfa() {
        return (AgentRunnable_Alfa) (super.get(alfaId).getRunnable());
    }

    public AgentRunnable_Bravo getBravo() {
        return (AgentRunnable_Bravo) (super.get(bravoId).getRunnable());
    }

}
