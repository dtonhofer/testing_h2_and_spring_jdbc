package name.heavycarbon.h2_exercises.transactions.deadlock_simple;

import name.heavycarbon.h2_exercises.transactions.agent.*;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AgentContainer_Deadlock extends AgentContainer {

    private final AgentId alfaId = new AgentId("alfa");
    private final AgentId bravoId = new AgentId("bravo");
    private final AppState appState = new AppState();

    public AgentContainer_Deadlock(
            @NotNull Db db,
            @NotNull Isol isol,
            @NotNull StuffId xId,
            @NotNull PrintException pex,
            @NotNull AgentRunnable_Alfa.Shifted shifted,
            @NotNull TransactionalGateway txGw) {
        final var alfa = new AgentRunnable_Alfa(db, appState, alfaId, isol, xId, pex, shifted, txGw);
        final var bravo = new AgentRunnable_Bravo(db, appState, bravoId, isol, xId, pex, txGw);
        setUnmodifiableAgentMap(List.of(new Agent(alfa), new Agent(bravo)));
    }

    public AgentRunnable_Alfa getAlfa() {
        return (AgentRunnable_Alfa) (super.get(alfaId).getRunnable());
    }

    public AgentRunnable_Bravo getBravo() {
        return (AgentRunnable_Bravo) (super.get(bravoId).getRunnable());
    }

}
