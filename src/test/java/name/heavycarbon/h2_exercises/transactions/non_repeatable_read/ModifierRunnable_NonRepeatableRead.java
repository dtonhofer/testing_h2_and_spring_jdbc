package name.heavycarbon.h2_exercises.transactions.non_repeatable_read;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainer.Op;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.common.AgentRunnableSyncOnAppStateInsideTransaction;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class ModifierRunnable_NonRepeatableRead extends AgentRunnableSyncOnAppStateInsideTransaction {

    // Instructions what to modify inside the modifying transaction

    private final Setup setup;

    // ---

    public ModifierRunnable_NonRepeatableRead(@NotNull Db db,
                                              @NotNull AppState appState,
                                              @NotNull AgentId agentId,
                                              @NotNull Isol isol,
                                              @NotNull Op op,
                                              @NotNull Setup setup,
                                              @NotNull TransactionalGateway txGw) {
        super(db, appState, agentId, isol, op, txGw);
        this.setup = setup;
    }

    // This is eventually called from the state machine loop inside a transaction

    protected void switchByAppState() throws InterruptedException {
        switch (getAppState().get()) {
            case 1 -> {
                switchByOp();
                incState();
                setTerminatedNicely();
                setStop();
            }
            default -> waitOnAppState();
        }
    }

    private void switchByOp() {
        switch (getOp()) {
            case Update -> {
                final var id = setup.getUpdateRow().getId();
                final var newValue = setup.getUpdateRow().getPayload();
                getDb().updatePayloadById(id, newValue);
            }
            case Insert -> {
                getDb().insert(setup.getInsertRow());
            }
            case Delete -> {
                final boolean success = getDb().deleteById(setup.getDeleteRow().getId());
                assert success;
            }
            default -> throw new IllegalArgumentException("Unhandled op " + getOp());
        }
    }

}
