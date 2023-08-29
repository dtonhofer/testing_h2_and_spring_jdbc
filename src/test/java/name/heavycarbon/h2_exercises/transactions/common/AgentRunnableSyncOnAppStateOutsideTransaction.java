package name.heavycarbon.h2_exercises.transactions.common;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainer.Op;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AgentRunnable;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import org.jetbrains.annotations.NotNull;

@Slf4j
public abstract class AgentRunnableSyncOnAppStateOutsideTransaction extends AgentRunnable {

    // Reference to a class that has a method annotated @Transactional

    @Getter
    private final TransactionalGateway txGw;

    // ---

    public AgentRunnableSyncOnAppStateOutsideTransaction(@NotNull Db db,
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
        synchronized (getAppState()) {
            log.info("{} in critical section.", getAgentId());
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
            switchByAppStateOutsideTransaction();
        }
    }

    protected abstract void switchByAppStateOutsideTransaction() throws InterruptedException;

}
