package name.heavycarbon.h2_exercises.transactions.non_repeatable_read;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainerAbstract.Op;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AgentRunnableAbstract;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.common.SetupForDirtyAndNonRepeatableRead;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import org.jetbrains.annotations.NotNull;

// ---
// Common code for an agent that "modifies" the database, i.e. updates, inserts
// or deletes records in "application state 1". It references the class providing
// the methods marked as @Transactional through the "modifierTx" field (actually an interface).
// ---

@Slf4j
public class ModifierRunnable_NonRepeatableRead extends AgentRunnableAbstract {

    private final TransactionalGateway txGw;

    // Instructions as to what modifications to apply to the database

    private final SetupForDirtyAndNonRepeatableRead mods;

    // ---

    public ModifierRunnable_NonRepeatableRead(@NotNull Db db,
                                              @NotNull AppState appState,
                                              @NotNull AgentId agentId,
                                              @NotNull Isol isol,
                                              @NotNull Op op,
                                              @NotNull SetupForDirtyAndNonRepeatableRead mods,
                                              @NotNull TransactionalGateway txGw) {
        super(db, appState, agentId, isol, op);
        this.txGw = txGw;
        this.mods = mods;
    }

    @Override
    public void run() {
        log.info("{} starting.", getAgentId());
        try {
            txGw.wrapIntoTransaction(this::syncOnAppState, getAgentId(), getIsol());
        } catch (Exception ex) {
            // Only Exceptions. Errors are let through
            exceptionMessage(log, ex);
        }
    }

    // Having opened a transaction, we are called back ... here!

    public void syncOnAppState() {
        synchronized (getAppState()) {
            log.info("{} in critical section.", getAgentId());
            catchInterruptedExceptionFromStateMachineLoop();
        }
    }

    private void catchInterruptedExceptionFromStateMachineLoop() {
        boolean interrupted = false; // set when the thread notices it has been interrupted
        try {
            runStateMachineLoop();
        } catch (InterruptedException ex) {
            interrupted = true;
        }
        log.info(finalMessage(interrupted));
    }

    private void runStateMachineLoop() throws InterruptedException {
        while (isContinue()) {
            switchByAppState();
        }
    }

    private void switchByAppState() throws InterruptedException {
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
                final var id = mods.getUpdateRow().getId();
                final var newValue = mods.getUpdateRow().getPayload();
                getDb().updatePayloadById(id, newValue);
            }
            case Insert -> {
                getDb().insert(mods.getInsertRow());
            }
            case Delete -> {
                final boolean success = getDb().deleteById(mods.getDeleteRow().getId());
                assert success;
            }
            default -> throw new IllegalArgumentException("Unhandled op " + getOp());
        }
    }


}
