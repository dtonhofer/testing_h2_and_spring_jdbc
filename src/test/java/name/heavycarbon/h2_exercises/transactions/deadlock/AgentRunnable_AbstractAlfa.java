package name.heavycarbon.h2_exercises.transactions.deadlock;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.*;
import name.heavycarbon.h2_exercises.transactions.common.Randomizer;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import org.jetbrains.annotations.NotNull;

@Slf4j
public abstract class AgentRunnable_AbstractAlfa extends AgentRunnable {

    // Reference to a class that has a method annotated @Transactional

    @Getter
    private final TransactionalGateway txGw;

    @NotNull
    protected final Config config;

    @NotNull
    protected final DbConfig dbConfig;

    public AgentRunnable_AbstractAlfa(@NotNull Db db,
                                      @NotNull AppState appState,
                                      @NotNull AgentId agentId,
                                      @NotNull TransactionalGateway txGw,
                                      @NotNull Config config,
                                      @NotNull DbConfig dbConfig) {
        super(db, appState, agentId, config.getIsol());
        this.txGw = txGw;
        this.config = config;
        this.dbConfig = dbConfig;
    }

    protected void startMessage() {
        log.info("'{}' starting", getAgentId());
        log.info("'{}' isolation level = '{}'", getAgentId(), config.getIsol());
        log.info("'{}' random startup delay = '{}'",getAgentId(), config.isRandomStartupDelay());
        log.info("'{}' alfa update timing = '{}'", getAgentId(), config.getAlfaUpdateTiming());;
    }

    @Override
    public void run() {
        setAgentStarted();
        startMessage();
        if (config.isRandomStartupDelay()) {
            Randomizer.randomizeStartup();
        }
        try {
            syncOnAppState();
        } catch (Exception ex) {
            // Note that Spring will have performed a ROLLBACK if:
            // - The "Throwable" is an "Error" or an unchecked "Exception"
            // or
            // - The "Exception" has been marked as causing ROLLBACK in the "@Transaction" annotation
            exceptionMessage(log, ex, config.getPex());
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
            // Call the abstract method...
            switchByAppStateOutsideTransaction();
        }
    }

    protected abstract void switchByAppStateOutsideTransaction() throws InterruptedException;

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

    protected abstract void runStateMachineLoopInsideTransaction() throws InterruptedException;

}
