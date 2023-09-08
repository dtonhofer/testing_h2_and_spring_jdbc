package name.heavycarbon.h2_exercises.transactions.deadlock_simple;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.common.AgentRunnableWithAllActionsInsideTransaction;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;

@Slf4j
public class AgentRunnable_Bravo extends AgentRunnableWithAllActionsInsideTransaction {

    @NotNull
    private final Config config;

    @NotNull
    private final DbConfig dbConfig;

    // Filled with the value of data items read

    @Getter
    private Stuff firstRead = null;

    @Getter
    private Exception exceptionSeen = null;

    public AgentRunnable_Bravo(@NotNull Db db,
                               @NotNull AppState appState,
                               @NotNull AgentId agentId,
                               @NotNull TransactionalGateway txGw,
                               @NotNull Config config,
                               @NotNull DbConfig dbConfig) {
        super(db, appState, agentId, config.getIsol(), config.getPex(), txGw);
        this.config = config;
        this.dbConfig = dbConfig;
    }

    @Override
    protected void startMessage() {
        super.startMessage();
        log.info("'{}' random startup delay = '{}'", getAgentId(), config.isRandomStartupDelay());
        log.info("'{}' bravo first op = '{}'", getAgentId(), config.getBravoFirstOp());
    }

    protected void switchByAppState() throws InterruptedException {
        switch (getAppState().get()) {
            case 2 -> {
                executeOpInState2();
                incAppState();
            }
            case 5 -> {
                // This will end in an exception in certain cases
                updateXInState5();
                // If everything is "smooth sailing", we return normally
                log.info("'{}' did not end with an exception", getAgentId());
                setAgentTerminatedNicely();
                setStop();
            }
            default -> waitOnAppState();
        }
    }

    private void executeOpInState2() {
        final Instant whenStarted = Instant.now();
        try {
            executeOpInState2_switch();
        } catch (Exception ex) {
            this.exceptionSeen = ex;
            long ms = Duration.between(whenStarted, Instant.now()).toMillis();
            log.info("'{}' got exception '{}' after waiting for {} ms.", getAgentId(), ex.getClass().getName(), ms);
            throw ex;
        }
    }

    private void executeOpInState2_switch() {
        switch (config.getBravoFirstOp()) {
            case ReadX -> getDb().readById(dbConfig.stuff_x.getId());
            case UpdateX -> getDb().updatePayloadById(dbConfig.stuff_x.getId(), dbConfig.updateString_bravo_did_x_early);
            case ReadZ -> getDb().readById(dbConfig.stuff_z.getId());
            case InsertZ -> getDb().insert(dbConfig.stuff_z);
            case UpdateZ -> getDb().updatePayloadById(dbConfig.stuff_z.getId(), dbConfig.updateString_bravo_did_z_early);
            case DeleteZ -> getDb().deleteById(dbConfig.stuff_z.getId());
            case ReadK -> getDb().readAlternateById(dbConfig.stuff_k.getId());
            case InsertK -> getDb().insertAlternate(dbConfig.stuff_k);
            case UpdateK -> getDb().updatePayloadByIdAlternate(dbConfig.stuff_k.getId(), dbConfig.updateString_bravo_did_k_early);
            case DeleteK -> getDb().deleteByIdAlternate(dbConfig.stuff_k.getId());
            default -> throw new IllegalArgumentException("Unhandled first top: " + config.getBravoFirstOp());
        }
    }

    private void updateXInState5() {
        final Instant whenStarted = Instant.now();
        try {
            final var id_x = dbConfig.stuff_x.getId();
            getDb().updatePayloadById(id_x, dbConfig.updateString_bravo_did_x);
        } catch (Exception ex) {
            this.exceptionSeen = ex;
            long ms = Duration.between(whenStarted, Instant.now()).toMillis();
            log.info("'{}' got exception '{}' after waiting for {} ms.", getAgentId(), ex.getClass().getName(), ms);
            throw ex;
        }
    }

}
