package name.heavycarbon.h2_exercises.transactions.deadlock_onlywriting;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainer;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.agent.PrintException;
import name.heavycarbon.h2_exercises.transactions.common.AgentRunnableWithAllActionsInsideTransaction;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;

@Slf4j
public class AgentRunnable_Bravo extends AgentRunnableWithAllActionsInsideTransaction {

    @Getter
    private Exception exceptionSeen = null;

    private final @NotNull Setup setup;

    public AgentRunnable_Bravo(@NotNull Db db,
                               @NotNull AppState appState,
                               @NotNull AgentId agentId,
                               @NotNull Isol isol,
                               @NotNull PrintException pex,
                               @NotNull Setup setup,
                               @NotNull TransactionalGateway txGw) {
        super(db, appState, agentId, isol, AgentContainer.Op.Unset, pex, txGw);
        this.setup = setup;
    }

    protected void switchByAppState() throws InterruptedException {
        switch (getAppState().get()) {
            case 2 -> {
                getDb().updatePayloadById(setup.bravoMarkerId(), "BRAVO WAS HERE");
                incState();
            }
            case 5 -> {
                incState();
                updatePayloadByIdExpectingException();
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
            getDb().updatePayloadById(setup.collisionId(), "UPDATED BY BRAVO");
        } catch (Exception ex) {
            this.exceptionSeen = ex;
            long ms = Duration.between(whenStarted, Instant.now()).toMillis();
            log.info("'{}' got exception '{}' after waiting for {} ms.", getAgentId(), ex.getClass().getName(), ms);
            throw ex;
        }
    }

}
