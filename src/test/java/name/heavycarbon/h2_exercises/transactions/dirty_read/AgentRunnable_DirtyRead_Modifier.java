package name.heavycarbon.h2_exercises.transactions.dirty_read;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainer.Op;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.agent.MyRollbackException;
import name.heavycarbon.h2_exercises.transactions.agent.PrintException;
import name.heavycarbon.h2_exercises.transactions.common.AgentRunnableWithAllActionsInsideTransaction;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class AgentRunnable_DirtyRead_Modifier extends AgentRunnableWithAllActionsInsideTransaction {

    // Instructions as to what modifications to apply to the database

    private final Setup setup;

    // ---

    public AgentRunnable_DirtyRead_Modifier(@NotNull Db db,
                                            @NotNull AppState appState,
                                            @NotNull AgentId agentId,
                                            @NotNull Isol isol,
                                            @NotNull Op op,
                                            @NotNull Setup setup,
                                            @NotNull TransactionalGateway txGw) {
        super(db, appState, agentId, isol, op, PrintException.No, txGw);
        this.setup = setup;
    }

    // Having opened a transaction, we are called back ... here!

    protected void switchByAppState() throws InterruptedException, MyRollbackException {
        switch (getAppState().get()) {
            case 0 -> {
                switchByOp();
                incState();
            }
            case 2 -> {
                // Do nothing except rolling back the transaction
                // by throwing an exception that is marked by annotation
                // as "causing rollback".
                // Note that we don't run any tests regarding "commit"
                // --> RuntimeException or Error also cause rollback but
                // checked exceptions do not by default!!
                incState();
                setThreadTerminatedNicely();;
                throw new MyRollbackException("Rolling back any modification");
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
