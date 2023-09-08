package name.heavycarbon.h2_exercises.transactions.sql_timeout;

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
    private final DbConfig dbConfig;

    @Getter
    private Exception exceptionSeen = null;

    @Getter
    private Stuff readInState3;

    public AgentRunnable_Bravo(@NotNull Db db,
                               @NotNull AppState appState,
                               @NotNull AgentId agentId,
                               @NotNull TransactionalGateway txGw,
                               @NotNull Config config,
                               @NotNull DbConfig dbConfig) {
        super(db, appState, agentId, config.isol(), config.pex(), txGw);
        this.dbConfig = dbConfig;
    }

    protected void switchByAppState() throws InterruptedException {
        switch (getAppState().get()) {
            case 1 -> {
                //getDb().updatePayloadById(setup.stuff_b().getId(), "BRAVO WAS HERE");
                incAppState();
            }
            case 3 -> {
                readInState3 = dbConfig.readX(getDb());
                // increment state immediately so that "alfa" can actually continue when this thread dies
                incAppState();
                updatePayloadByIdExpectingException();
                log.info("'{}' did not end with an exception!?", getAgentId());
                setAgentTerminatedNicely();
                setStop();
            }
            default -> waitOnAppState();
        }
    }

    // At this point, the "alfa" agent owns the write lock on the row and won't give it up!
    // So updating here will lead to an
    // org.h2.jdbc.JdbcSQLTimeoutException
    // which will be the cause of an
    // org.springframework.dao.QueryTimeoutException
    // The log also says: "Application exception overridden by rollback exception" which is what we expect.
    //
    // Empirically lock timeout is a few ms more than 2 seconds
    // Although
    // https://www.h2database.com/html/commands.html#set_default_lock_timeout
    // says it is actually 1 seconds
    // See:
    // SELECT * FROM INFORMATION_SCHEMA.SETTINGS  WHERE SETTING_NAME = 'DEFAULT_LOCK_TIMEOUT'
    // Try:
    // SET DEFAULT_LOCK_TIMEOUT 500
    // See the difference.
    // Lock timeout per session:
    // https://www.h2database.com/html/commands.html#set_lock_timeout

    private void updatePayloadByIdExpectingException() {
        final Instant whenStarted = Instant.now();
        try {
            // >>>>
            dbConfig.updateX(getDb(), dbConfig.updateString_bravo_did_x);
            // <<<<
        } catch (Exception ex) {
            this.exceptionSeen = ex;
            long ms = Duration.between(whenStarted, Instant.now()).toMillis();
            log.info("'{}' got exception '{}' after waiting for {} ms.", getAgentId(), ex.getClass().getName(), ms);
            throw ex;
        }
    }

}
