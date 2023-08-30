package name.heavycarbon.h2_exercises.transactions.deadlock;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainer.Op;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AgentRunnable;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.agent.PrintException;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class AgentRunnable_Alfa extends AgentRunnable {

    private final @NotNull Setup setup;

    // Reference to a class that has a method annotated @Transactional

    @Getter
    private final TransactionalGateway txGw;

    private final PrintException printException;

    // ---

    public AgentRunnable_Alfa(@NotNull Db db,
                              @NotNull AppState appState,
                              @NotNull AgentId agentId,
                              @NotNull Isol isol,
                              @NotNull Setup setup,
                              @NotNull PrintException printException,
                              @NotNull TransactionalGateway txGw) {
        super(db, appState, agentId, isol, Op.Unset);
        this.txGw = txGw;
        this.setup = setup;
        this.printException = printException;
    }

    @Override
    public void run() {
        setThreadStarted();
        log.info("'{}' starting.", getAgentId());
        syncOnAppState();
    }

    private void syncOnAppState() {
        synchronized (getAppState()) {
            log.info("'{}' in critical section.", getAgentId());
            catchInterruptedExceptionFromStateMachineLoop();
        }
    }

    private void catchInterruptedExceptionFromStateMachineLoop() {
        boolean interrupted = false; // set when the thread notices it has been interrupted
        try {
            runStateMachineLoopOutsideTransaction();
        } catch (InterruptedException ex) {
            interrupted = true;
        }
        log.info(finalMessage(interrupted));
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
                enterTransaction(); // can throw
            }
            case 4 -> {
                setThreadTerminatedNicely();
                incState();
                setStop();
            }
            default -> waitOnAppState(); // can throw
        }
    }

    public void enterTransaction() throws InterruptedException {
        try {
            // >>> call a method marked @Transactional on an instance injected by Spring
            txGw.wrapIntoTransaction(this::runStateMachineLoopInsideTransaction, getAgentId(), getIsol());
            // <<<
        } catch (InterruptedException ex) {
            throw ex;
        } catch (Exception ex) {
            // Catch only Exceptions. Errors are let through!
            // Note that Spring will have performed a ROLLBACK if:
            // - The "Throwable" is an "Error" or an unchecked "Exception"
            // or
            // - The "Exception" has been marked as causing ROLLBACK in the "@Transaction" annotation
            exceptionMessage(log, ex, printException);
        }
    }

    private boolean isStatInsideTransaction() {
        int state = getAppState().get();
        return 0 <= state && state <= 3;
    }

    public void runStateMachineLoopInsideTransaction() throws InterruptedException {
        while (isContinue() && isStatInsideTransaction()) {
            log.info("'{}' now working in state {} (inside transaction)", getAgentId(), getAppState());
            switchByAppStateInsideTransaction();
        }
    }

    protected void switchByAppStateInsideTransaction() throws InterruptedException {
        switch (getAppState().get()) {
            case 0 -> {
                if (setup.withMarkers()) {
                    getDb().updatePayloadById(setup.stuff_a().getId(), "ALFA WAS HERE");
                }
                incState();
            }
            case 3 -> {
                getDb().updatePayloadById(setup.stuff_x().getId(), "UPDATED BY ALFA");
                incState();
            }
            default -> waitOnAppState();
        }
    }

}
