package name.heavycarbon.h2_exercises.transactions.deadlock_simple;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainer.Op;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.agent.PrintException;
import name.heavycarbon.h2_exercises.transactions.common.AgentRunnableWithAllActionsInsideTransaction;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;

@Slf4j
public class AgentRunnable_Bravo extends AgentRunnableWithAllActionsInsideTransaction {

    private final @NotNull StuffId xId;

    // Filled with the exception thrown when deadlock is detected

    @Getter
    private Exception exceptionSeen = null;

    // Filled with the value of data item X read in state 1 and 3

    @Getter
    private Stuff readInState1 = null;

    @Getter
    private Stuff readInState4 = null;

    public AgentRunnable_Bravo(@NotNull Db db,
                               @NotNull AppState appState,
                               @NotNull AgentId agentId,
                               @NotNull Isol isol,
                               @NotNull StuffId xId,
                               @NotNull PrintException pex,
                               @NotNull TransactionalGateway txGw) {
        super(db, appState, agentId, isol, Op.Unset, pex, txGw);
        this.xId = xId;
    }

    protected void switchByAppState() throws InterruptedException {
        switch (getAppState().get()) {
            case 1 -> {

                incState();
            }
            case 3 -> {
                incState();
                readInState1 = getDb().readById(xId).orElseThrow();
            }
            case 6 -> {
                // reading for fun, this doesn't chnage beahviour
                //readInState4 = getDb().readById(xId).orElseThrow();
                // >>> This will end in deadlock exception
                updatePayloadByIdExpectingException();
                // <<<
                log.info("'{}' did not end with an exception!?", getAgentId());
                setThreadTerminatedNicely();
                setStop();
            }
            default -> waitOnAppState();
        }
    }

    private void updatePayloadByIdExpectingException() {
        final Instant whenStarted = Instant.now();
        try {
            getDb().updatePayloadById(xId, "UPDATED BY BRAVO");
        } catch (Exception ex) {
            this.exceptionSeen = ex;
            long ms = Duration.between(whenStarted, Instant.now()).toMillis();
            log.info("'{}' got exception '{}' after waiting for {} ms.", getAgentId(), ex.getClass().getName(), ms);
            throw ex;
        }
    }

}
