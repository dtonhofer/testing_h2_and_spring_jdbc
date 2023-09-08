package name.heavycarbon.h2_exercises.transactions.common;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.*;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import org.jetbrains.annotations.NotNull;

@Slf4j
public abstract class AgentRunnableWithAllActionsInsideTransaction extends AgentRunnable {

    // Reference to a class that has a method annotated @Transactional

    @Getter
    @NotNull
    private final TransactionalGateway txGw;

    @NotNull
    private final PrintException pex;

    public AgentRunnableWithAllActionsInsideTransaction(@NotNull Db db,
                                                        @NotNull AppState appState,
                                                        @NotNull AgentId agentId,
                                                        @NotNull Isol isol,
                                                        @NotNull PrintException pex,
                                                        @NotNull TransactionalGateway txGw) {
        super(db, appState, agentId, isol);
        this.txGw = txGw;
        this.pex = pex;
    }

    protected void startMessage() {
        log.info("'{}' starting", getAgentId());
        log.info("'{}' isolation level = '{}'", getAgentId(), getIsol());
    }

    @Override
    public void run() {
        setAgentStarted();
        startMessage();
        // not really needed
        // TODO: Should be configurable.
        Randomizer.randomizeStartup();
        try {
            enterTransaction();
        } catch (Exception ex) {
            // Note that Spring will have performed a ROLLBACK if:
            // - The "Throwable" is an "Error" or an unchecked "Exception"
            // or
            // - The "Exception" has been marked as causing ROLLBACK in the "@Transaction" annotation
            exceptionMessage(log, ex, pex);
        }
    }

    private void enterTransaction() throws MyRollbackException, InterruptedException {
        try {
            log.info("'{}' entering transaction.", getAgentId());
            // call a method marked @Transactional on an instance injected by Spring
            txGw.wrapIntoTransaction(this::syncOnAppState, getAgentId(), getIsol());
        } finally {
            log.info("'{}' out of transaction.", getAgentId());
        }
    }

    // Having opened a transaction, we are called back ... here!

    public void syncOnAppState() throws MyRollbackException {
        try {
            synchronized (getAppState()) {
                log.info("'{}' in critical section.", getAgentId());
                catchInterruptedExceptionFromStateMachineLoop();
            }
        } finally {
            log.info("'{}' out of critical section.", getAgentId());
        }
    }

    private void catchInterruptedExceptionFromStateMachineLoop() throws MyRollbackException {
        boolean interrupted = false; // set when the thread notices it has been interrupted
        try {
            runStateMachineLoop();
        } catch (InterruptedException ex) {
            interrupted = true;
        } finally {
            log.info(finalMessage(interrupted));
        }
    }

    private void runStateMachineLoop() throws InterruptedException, MyRollbackException {
        while (isContinue()) {
            log.info("'{}' now working in state {} (inside transaction)", getAgentId(), getAppState());
            switchByAppState();
        }
    }

    protected abstract void switchByAppState() throws InterruptedException, MyRollbackException;

}
