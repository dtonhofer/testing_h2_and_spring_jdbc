package name.heavycarbon.h2_exercises.transactions.non_repeatable_read;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.common.AgentRunnableWithAllActionsInsideTransaction;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class AgentRunnable_Modifier extends AgentRunnableWithAllActionsInsideTransaction {

    @NotNull
    private final Config config;
    @NotNull
    private final DbConfig dbConfig;

    public AgentRunnable_Modifier(@NotNull Db db,
                                  @NotNull AppState appState,
                                  @NotNull AgentId agentId,
                                  @NotNull TransactionalGateway txGw,
                                  @NotNull Config config,
                                  @NotNull DbConfig dbConfig) {
        super(db, appState, agentId, config.isol(), config.pex(), txGw);
        this.config = config;
        this.dbConfig = dbConfig;
    }

    // This is eventually called from the state machine loop inside a transaction

    protected void switchByAppState() throws InterruptedException {
        switch (getAppState().get()) {
            case 1 -> {
                switchByOp();
                incAppState();
                setAgentTerminatedNicely();
                setStop();
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
