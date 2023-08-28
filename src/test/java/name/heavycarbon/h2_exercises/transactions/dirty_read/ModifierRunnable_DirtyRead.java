package name.heavycarbon.h2_exercises.transactions.dirty_read;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainerAbstract.Op;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AgentRunnableAbstract;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.agent.MyRollbackException;
import name.heavycarbon.h2_exercises.transactions.common.SetupForDirtyAndNonRepeatableRead;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway_Throwing;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class ModifierRunnable_DirtyRead extends AgentRunnableAbstract {

    private final TransactionalGateway_Throwing txGw;

    // Instructions as to what modifications to apply to the database

    private final SetupForDirtyAndNonRepeatableRead mods;

    // ---

    public ModifierRunnable_DirtyRead(@NotNull Db db,
                                      @NotNull AppState appState,
                                      @NotNull AgentId agentId,
                                      @NotNull Isol isol,
                                      @NotNull Op op,
                                      @NotNull SetupForDirtyAndNonRepeatableRead mods,
                                      @NotNull TransactionalGateway_Throwing txGw) {
        super(db, appState, agentId, isol, op);
        this.txGw = txGw;
        this.mods = mods;
    }

    @Override
    public void run() {
        log.info("{} starting.", getAgentId());
        try {
            // >>> call a method marked @Transactional on an instance injected by Spring
            // this call is actually expected to throw "MyRollbackException"
            txGw.wrapIntoTransaction(this::syncOnAppState, getAgentId(), getIsol());
            // <<<
        } catch (Exception ex) {
            // Only Exceptions, Errors are let through
            exceptionMessage(log, ex);
        }
    }

    // Having opened a transaction, we are called back ... here!

    public void syncOnAppState() throws MyRollbackException {
        synchronized (getAppState()) {
            log.info("{} in critical section.", getAgentId());
            catchInterruptedExceptionFromStateMachineLoop();
        }
    }

    private void catchInterruptedExceptionFromStateMachineLoop() throws MyRollbackException {
        boolean interrupted = false; // set when the thread notices it has been interrupted
        try {
            runStateMachineLoop();
        } catch (InterruptedException ex) {
            interrupted = true;
        }
        log.info(finalMessage(interrupted));
    }

    private void runStateMachineLoop() throws InterruptedException, MyRollbackException {
        while (isContinue()) {
            switchByAppState();
        }
    }

    private void switchByAppState() throws InterruptedException, MyRollbackException {
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
                setTerminatedNicely();
                throw new MyRollbackException("Rolling back any modification");
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
