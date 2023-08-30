package name.heavycarbon.h2_exercises.transactions.sql_timeout;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainer;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.agent.PrintException;
import name.heavycarbon.h2_exercises.transactions.common.AgentRunnableWithAllActionsInsideTransaction;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class AgentRunnable_Alfa extends AgentRunnableWithAllActionsInsideTransaction {

    private final @NotNull Setup setup;

    public AgentRunnable_Alfa(@NotNull Db db,
                              @NotNull AppState appState,
                              @NotNull AgentId agentId,
                              @NotNull Isol isol,
                              @NotNull Setup setup,
                              @NotNull PrintException pex,
                              @NotNull TransactionalGateway txGw) {
        super(db, appState, agentId, isol, AgentContainer.Op.Unset, pex, txGw);
        this.setup = setup;
    }

    // The only method that is being added

    protected void switchByAppState() throws InterruptedException {
        switch (getAppState().get()) {
            case 0 -> {
                getDb().updatePayloadById(setup.stuff_a().getId(), "ALFA WAS HERE");
                incState();
            }
            case 2 -> {
                getDb().updatePayloadById(setup.stuff_x().getId(), "UPDATED BY ALFA");
                incState();
            }
            case 4 -> {
                setThreadTerminatedNicely();
                incState();
                setStop();
            }
            default -> waitOnAppState();
        }
    }

}
