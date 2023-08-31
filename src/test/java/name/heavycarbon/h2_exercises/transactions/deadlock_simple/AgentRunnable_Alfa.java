package name.heavycarbon.h2_exercises.transactions.deadlock_simple;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.*;
import name.heavycarbon.h2_exercises.transactions.common.Randomizer;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class AgentRunnable_Alfa extends AgentRunnable {

    // Reference to a class that has a method annotated @Transactional

    @Getter
    private final TransactionalGateway txGw;

    private final @NotNull StuffId xId;

    private final @NotNull PrintException pex;

    private final @NotNull Shifted shifted;

    public enum Shifted { Yes, No };

    // ---

    public AgentRunnable_Alfa(@NotNull Db db,
                              @NotNull AppState appState,
                              @NotNull AgentId agentId,
                              @NotNull Isol isol,
                              @NotNull StuffId xId,
                              @NotNull PrintException pex,
                              @NotNull Shifted shifted,
                              @NotNull TransactionalGateway txGw) {
        super(db, appState, agentId, isol, AgentContainer.Op.Unset);
        this.txGw = txGw;
        this.xId = xId;
        this.pex = pex;
        this.shifted = shifted;
    }

    @Override
    public void run() {
        setThreadStarted();
        log.info("'{}' starting.", getAgentId());
        Randomizer.randomizeStartup();
        try {
            syncOnAppState();
        } catch (Exception ex) {
            // Note that Spring will have performed a ROLLBACK if:
            // - The "Throwable" is an "Error" or an unchecked "Exception"
            // or
            // - The "Exception" has been marked as causing ROLLBACK in the "@Transaction" annotation
            exceptionMessage(log, ex, pex);
        }
    }

    public void syncOnAppState() {
        try {
            synchronized (getAppState()) {
                log.info("'{}' in critical section.", getAgentId());
                catchInterruptedExceptionFromStateMachineLoop();
            }
        } finally {
            log.info("'{}' out of critical section.", getAgentId());
        }
    }

    private void catchInterruptedExceptionFromStateMachineLoop() {
        boolean interrupted = false; // set when the thread notices it has been interrupted
        try {
            runStateMachineLoopOutsideTransaction();
        } catch (InterruptedException ex) {
            interrupted = true;
        } finally {
            log.info(finalMessage(interrupted));
        }
    }

    private void runStateMachineLoopOutsideTransaction() throws InterruptedException {
        while (isContinue()) {
            log.info("'{}' now working in state {} (outside transaction)", getAgentId(), getAppState());
            switchByAppStateOutsideTransaction();
        }
    }

    private void switchByAppStateOutsideTransaction() throws InterruptedException {
        switch (getAppState().get()) {
            case 0 -> {
                incState();
                enterTransaction();
            }
            case 4 -> {
                incState();
                setThreadTerminatedNicely();
                setStop();
            }
            default -> waitOnAppState();
        }
    }

    public void enterTransaction() throws InterruptedException {
        try {
            log.info("'{}' entering transaction.", getAgentId());
            // call a method marked @Transactional on an instance injected by Spring
            txGw.wrapIntoTransaction(this::runStateMachineLoopInsideTransaction, getAgentId(), getIsol());
        } catch (MyRollbackException ex) {
            // So we wanted to roll back, maybe the caller wants to know?
            // For now the requirements are unclear, just do nothing.
        } finally {
            log.info("'{}' out of transaction.", getAgentId());
        }
    }

    private boolean isStatInsideTransaction() {
        int state = getAppState().get();
        return 0 < state && state < 4;
    }

    public void runStateMachineLoopInsideTransaction() throws InterruptedException {
        while (isContinue() && isStatInsideTransaction()) {
            log.info("'{}' now working in state {} (inside transaction)", getAgentId(), getAppState());
            switchByAppStateInsideTransaction();
        }
    }

    protected void switchByAppStateInsideTransaction() throws InterruptedException {
        switch (getAppState().get()) {
            case 1 -> {
                if (shifted == Shifted.Yes) {
                    getDb().updatePayloadById(xId, "UPDATED BY ALFA");
                }
                incState();
            }
            case 3 -> {
                if (shifted == Shifted.No) {
                    getDb().updatePayloadById(xId, "UPDATED BY ALFA");
                }
                incState();
            }
            default -> waitOnAppState();
        }
    }

}
