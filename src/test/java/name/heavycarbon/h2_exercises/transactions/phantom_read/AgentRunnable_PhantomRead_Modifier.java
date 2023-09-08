package name.heavycarbon.h2_exercises.transactions.phantom_read;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.common.AgentRunnableWithAllActionsInsideTransaction;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.EnsembleId;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class AgentRunnable_PhantomRead_Modifier extends AgentRunnableWithAllActionsInsideTransaction {

    @NotNull
    private final Config config;
    @NotNull
    private final DbConfig dbConfig;

    public AgentRunnable_PhantomRead_Modifier(@NotNull Db db,
                                              @NotNull AppState appState,
                                              @NotNull AgentId agentId,
                                              @NotNull TransactionalGateway txGw,
                                              @NotNull Config config,
                                              @NotNull DbConfig dbConfig) {
        super(db, appState, agentId, config.isol(), config.pex(), txGw);
        this.config = config;
        this.dbConfig = dbConfig;
    }

    // ---
    // This is eventually called from the state machine loop inside a transaction
    // ---

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
            case Insert -> getDb().insert(dbConfig.insertMe);
            case Delete -> getDb().deleteById(dbConfig.deleteMe.getId());
            case UpdateInto -> {
                final StuffId id = dbConfig.updateForMovingIn.getId();
                final String newPayload = dbConfig.updateForMovingInChanged.getPayload();
                final EnsembleId newEnsembleId = dbConfig.updateForMovingInChanged.getEnsembleId();
                getDb().updateEnsembleById(id, newEnsembleId);
                getDb().updatePayloadById(id, newPayload);
            }
            case UpdateOutOf -> {
                final StuffId id = dbConfig.updateForMovingOut.getId();
                final String newPayload = dbConfig.updateForMovingOutChanged.getPayload();
                final EnsembleId newEnsembleId = dbConfig.updateForMovingOutChanged.getEnsembleId();
                getDb().updateEnsembleById(id, newEnsembleId);
                getDb().updatePayloadById(id, newPayload);
            }
            default -> throw new IllegalArgumentException("Unhandled op " + config.op());
        }
    }

}
