package name.heavycarbon.h2_exercises.transactions.deadlock_simple;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;

@Slf4j
public class AgentRunnable_SubAlfa extends AgentRunnable_Alfa {

    @Getter
    private Exception exceptionSeen = null;

    public AgentRunnable_SubAlfa(
            @NotNull Db db,
            @NotNull AppState appState,
            @NotNull AgentId agentId,
            @NotNull TransactionalGateway txGw,
            @NotNull Config config,
            @NotNull DbConfig dbConfig) {
        super(db, appState, agentId, txGw, config, dbConfig);
    }

    protected void switchByAppStateOutsideTransaction() throws InterruptedException {
        switch (getAppState().get()) {
            case 0 -> {
                incAppState();
                // This will enter the transaction, then call runStateMachineLoopInsideTransaction()
                enterTransaction();
            }
            case 4 -> {
                incAppState();
                setAgentTerminatedNicely();
                setStop();
            }
            default -> waitOnAppState();
        }
    }

    // TODO This approach is error prone ...
    // TODO States should tell about themselves whether they are inside a transaction

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
                if (config.isAlfaUpdateEarly()) {
                    updateX();
                }
                incAppState();
            }
            case 3 -> {
                if (config.isAlfaUpdateLate()) {
                    updateX();
                }
                incAppState();
            }
            default -> waitOnAppState();
        }
    }

    private void updateX() {
        final Instant whenStarted = Instant.now();
        try {
            dbConfig.updateX(getDb(), dbConfig.updateString_alfa_did_x);
        } catch (Exception ex) {
            this.exceptionSeen = ex;
            long ms = Duration.between(whenStarted, Instant.now()).toMillis();
            log.info("'{}' got exception '{}' after waiting for {} ms.", getAgentId(), ex.getClass().getName(), ms);
            throw ex;
        }
    }

}
