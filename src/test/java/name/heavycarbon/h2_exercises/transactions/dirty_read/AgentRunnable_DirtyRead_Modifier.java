package name.heavycarbon.h2_exercises.transactions.dirty_read;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.agent.MyRollbackException;
import name.heavycarbon.h2_exercises.transactions.common.AgentRunnableWithAllActionsInsideTransaction;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class AgentRunnable_DirtyRead_Modifier extends AgentRunnableWithAllActionsInsideTransaction {

    private final Config config;
    private final DbConfig dbConfig;

    public AgentRunnable_DirtyRead_Modifier(@NotNull Db db,
                                            @NotNull AppState appState,
                                            @NotNull AgentId agentId,
                                            @NotNull TransactionalGateway txGw,
                                            @NotNull Config config,
                                            @NotNull DbConfig dbConfig) {
        super(db, appState, agentId, config.isol(), config.pex(), txGw);
        this.config = config;
        this.dbConfig = dbConfig;
    }

    // Having opened a transaction, we are called back ... here!

    protected void switchByAppState() throws InterruptedException, MyRollbackException {
        switch (getAppState().get()) {
            case 0 -> {
                switchByOp();
                incAppState();
            }
            case 2 -> {
                // Do nothing except rolling back the transaction
                // by throwing an exception that is marked by annotation
                // as "causing rollback".
                // Note that we don't run any tests regarding "commit"
                // --> RuntimeException or Error also cause rollback but
                // checked exceptions do not by default!!
                incAppState();
                setAgentTerminatedNicely();
                throw new MyRollbackException("Rolling back any modification");
            }
            default -> waitOnAppState();
        }
    }

    private void switchByOp() {
        switch (config.op()) {
            case Update -> {
                final var id = dbConfig.updateRow.getId();
                final var newValue = dbConfig.updateRow.getPayload();
                getDb().updatePayloadById(id, newValue);
            }
            case Insert -> {
                getDb().insert(dbConfig.insertRow);
            }
            case Delete -> {
                final boolean success = getDb().deleteById(dbConfig.deleteRow.getId());
                assert success;
            }
            default -> throw new IllegalArgumentException("Unhandled op " + config.op());
        }
    }
}
