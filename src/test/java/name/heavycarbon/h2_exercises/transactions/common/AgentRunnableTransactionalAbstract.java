package name.heavycarbon.h2_exercises.transactions.common;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.*;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainerAbstract.Op;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import org.jetbrains.annotations.NotNull;

@Slf4j
public abstract class AgentRunnableTransactionalAbstract extends AgentRunnableAbstract {

    // Reference to a class that has a method annotated @Transactional

    @Getter
    private final TransactionalGateway txGw;

    // ---

    public AgentRunnableTransactionalAbstract(@NotNull Db db,
                                              @NotNull AppState appState,
                                              @NotNull AgentId agentId,
                                              @NotNull Isol isol,
                                              @NotNull Op op,
                                              @NotNull TransactionalGateway txGw) {
        super(db, appState, agentId, isol, op);
        this.txGw = txGw;
    }

    @Override
    public void run() {
        log.info("{} starting.", getAgentId());
        try {
            // >>> call a method marked @Transactional on an instance injected by Spring
            txGw.wrapIntoTransaction(this::syncOnAppState, getAgentId(), getIsol());
            // <<<
        } catch (Exception ex) {
            // Only Exceptions. Errors are let through
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

    protected abstract void switchByAppState() throws InterruptedException, MyRollbackException;

}
