package name.heavycarbon.h2_exercises.transactions.sql_timeout;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainerAbstract;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.common.AgentRunnableTransactionalAbstract;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class Runnable_Alfa extends AgentRunnableTransactionalAbstract {

    private final @NotNull Setup setup;

    public Runnable_Alfa(@NotNull Db db,
                         @NotNull AppState appState,
                         @NotNull AgentId agentId,
                         @NotNull Isol isol,
                         @NotNull Setup setup,
                         @NotNull TransactionalGateway txGw) {
        super(db, appState, agentId, isol, AgentContainerAbstract.Op.Unset, txGw);
        this.setup = setup;
    }

    // The only method that is being added

    protected void switchByAppState() throws InterruptedException {
        log.info("{} now working in state {}", getAgentId(), getAppState());
        switch (getAppState().get()) {
            case 0 -> {
                getDb().updatePayloadById(setup.stuff_a().getId(), "ALFA WAS HERE");
                incState();
            }
            case 2 -> {
                getDb().updatePayloadById(setup.stuff_x().getId(), "UPDATED BY ALFA");
                // do not commit yet
                incState();
            }
            case 4 -> {
                setTerminatedNicely();
                incState();
                setStop();
            }
            default -> waitOnAppState();
        }
    }

}
