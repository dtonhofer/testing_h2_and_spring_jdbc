package name.heavycarbon.h2_exercises.transactions.write_same_row_cannot_get_lock;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentRunnableBase;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import name.heavycarbon.h2_exercises.transactions.write_same_row.Transactional_WSR;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;

@Slf4j
public class Transactional_WSR_WriteLast extends Transactional_WSR {

    // This is called by a thread which already owns the monitor to AppState.

    protected void writeAccordingToAppState(@NotNull AgentRunnableBase ar, @NotNull StuffId markerId, @NotNull StuffId collisionId) throws InterruptedException {
        switch (ar.appState.get()) {
            case 1 -> {
                log.info("{} in state 1. Writing to row with the marker id.", ar.agentId);
                db.updateById(markerId, "WRITE LAST WAS HERE");
                ar.incState();
            }
            case 3 -> {
                log.info("{} in state 3. Writing to row with the collision id.", ar.agentId);
                // At this point, the "write first" agent owns the write lock on the row and won't give it up!
                // So updating here will lead to an
                // org.h2.jdbc.JdbcSQLTimeoutException
                // which will be the cause of an
                // org.springframework.dao.QueryTimeoutException
                // The log also says: "Application exception overridden by rollback exception" which is what we expect.
                Instant whenStarted = Instant.now();
                try {
                    db.updateById(collisionId, "IF YOU SEE THIS, WRITE LAST WINS");
                } catch (Throwable th) {
                    //
                    // If the lock cannot be acquired:
                    // ===============================
                    //
                    // Empirically lock timeout is a few ms more than 2s
                    // Although
                    // https://www.h2database.com/html/commands.html#set_default_lock_timeout
                    // says it is actually 1s
                    // See:
                    // SELECT * FROM INFORMATION_SCHEMA.SETTINGS  WHERE SETTING_NAME = 'DEFAULT_LOCK_TIMEOUT'
                    // Try:
                    // SET DEFAULT_LOCK_TIMEOUT 500
                    // See the difference.
                    // Lock timeout per session:
                    // https://www.h2database.com/html/commands.html#set_lock_timeout
                    //
                    // If a deadlock was detected
                    // ==========================
                    //
                    // That's immediate, 0s of waiting
                    //
                    Instant whenEnded = Instant.now();
                    long ms = Duration.between(whenStarted, whenEnded).toMillis();
                    log.info("{} got exception after waiting for {} ms.", ar.agentId, ms);
                    //
                    // Rethrow, leading to a rollback!
                    //
                    throw th;
                }
                log.info("{} did not end with an exception.", ar.agentId);
                // If the agent did terminate with an exception it will have "terminated badly"
                // and that will be picked up by the "write first" agent in its loop, and thus
                // the "write first" agent will ALSO terminate. Otherwise, proceed as usual:
                ar.incState();
                ar.setTerminatedNicely();
                ar.setStop();
            }
            default -> {
                // wait, relinquishing monitor (but don't wait forever)
                log.info("{} waiting.", ar.agentId);
                ar.waitOnAppState();
            }
        }
    }

}