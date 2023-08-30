package name.heavycarbon.h2_exercises.transactions.deadlock;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainer.Op;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.common.AgentRunnableWithAllActionsInsideTransaction;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;

@Slf4j
public class AgentRunnable_Bravo extends AgentRunnableWithAllActionsInsideTransaction {

    private final @NotNull Setup setup;

    @Getter
    private Exception exceptionSeen = null;

    @Getter
    private Stuff readInState2;

    @Getter
    private Stuff readInState5;

    public AgentRunnable_Bravo(@NotNull Db db,
                               @NotNull AppState appState,
                               @NotNull AgentId agentId,
                               @NotNull Isol isol,
                               @NotNull Setup setup,
                               @NotNull TransactionalGateway txGw) {
        super(db, appState, agentId, isol, Op.Unset, PrintException.No, txGw);
        this.setup = setup;
    }

    protected void switchByAppState() throws InterruptedException {
        switch (getAppState().get()) {
            case 1 -> {
                if (setup.withMarkers()) {
                    // agent Bravo writing a marker will create a deadlock
                    getDb().updatePayloadById(setup.stuff_b().getId(), "BRAVO WAS HERE");
                }
                incState();
            }
            case 2 -> {
                if (setup.withReadingInState2()) {
                    // agent Bravo reading in state 2 will create a deadlock
                    readInState2 = getDb().readById(setup.stuff_x().getId()).orElseThrow();
                }
                incState();
            }
            case 5 -> {
                // read again before updating to see whether our assumptions are right
                // whether this is done or not does not change whether a deadlock occurs
                if (setup.withReadingInState5()) {
                    readInState5 = getDb().readById(setup.stuff_x().getId()).orElseThrow();
                }
                updatePayloadByIdExpectingException();
                log.info("'{}' did not end with an exception!?", getAgentId());
                setTerminatedNicely();
                setStop();
            }
            default -> waitOnAppState();
        }
    }

    private void updatePayloadByIdExpectingException() {
        final Instant whenStarted = Instant.now();
        try {
            // >>>>
            getDb().updatePayloadById(setup.stuff_x().getId(), "UPDATED BY BRAVO");
            // <<<<
        } catch (Exception ex) {
            this.exceptionSeen = ex;
            long ms = Duration.between(whenStarted, Instant.now()).toMillis();
            log.info("'{}' got exception '{}' after waiting for {} ms.", getAgentId(), ex.getClass().getName(), ms);
            throw ex;
        }
    }

}
