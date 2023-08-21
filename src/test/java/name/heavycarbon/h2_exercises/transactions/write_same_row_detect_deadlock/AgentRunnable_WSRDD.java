package name.heavycarbon.h2_exercises.transactions.write_same_row_detect_deadlock;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AgentRunnableBase;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import name.heavycarbon.h2_exercises.transactions.session.Isol;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@Slf4j
public class AgentRunnable_WSRDD extends AgentRunnableBase {

    private final Transactional_WSRDD transactional;

    // mark this with an agent specific message (to see whether anything was rolled back later)

    private final StuffId markerId;

    // try to write a "winner" message to here

    private final StuffId collisionId;

    // if an exception is thrown, it is stored here

    @Getter
    private Optional<Throwable> caughtThrowable = Optional.empty();

    public AgentRunnable_WSRDD(@NotNull Db db,
                               @NotNull StuffId markerId,
                               @NotNull StuffId collisionId,
                               @NotNull AppState appState,
                               @NotNull AgentId agentId,
                               @NotNull Isol isol,
                               @NotNull Transactional_WSRDD transactional) {
        super(db, appState, agentId, isol);
        this.transactional = transactional;
        this.markerId = markerId;
        this.collisionId = collisionId;
    }

    @Override
    public void run() {
        try {
            synchronized (appState) {
                log.info("{} in critical section", agentId);
                log.info("{} about to enter transaction", agentId);
                transactional.runInsideTransaction(this, markerId, collisionId);
                log.info("{} has exited transaction", agentId);
                // Now increment the state (we still have the monitor) to liberate the other agent!
                // We are already stopping.
                incState();
                setTerminatedNicely();
            }
            log.info("{} out of critical section", agentId);
        } catch (Throwable th) {
            AgentRunnableBase.throwableMessage(log, agentId, th);
            caughtThrowable = Optional.of(th);
        }
    }
}
