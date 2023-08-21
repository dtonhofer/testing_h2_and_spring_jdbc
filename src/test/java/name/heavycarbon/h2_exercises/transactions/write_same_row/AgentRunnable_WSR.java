package name.heavycarbon.h2_exercises.transactions.write_same_row;

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
public class AgentRunnable_WSR extends AgentRunnableBase {

    private final Transactional_WSR transactional;

    // mark this with an agent specific message (to see whether anything was rolled back later)

    private final StuffId markerId;

    // try to write a "winner" message to here

    private final StuffId collisionId;

    // if an exception is thrown, it is stored here

    @Getter
    private Optional<Throwable> caughtThrowable = Optional.empty();

    // ---

    public AgentRunnable_WSR(@NotNull Db db,
                             @NotNull StuffId markerId,
                             @NotNull StuffId collisionId,
                             @NotNull AppState appState,
                             @NotNull AgentId agentId,
                             @NotNull Isol isol,
                             @NotNull Transactional_WSR transactional) {
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
            }
            log.info("{} out of critical section", agentId);
        } catch (Throwable th) {
            AgentRunnableBase.throwableMessage(log, agentId, th);
            caughtThrowable = Optional.of(th);
        }
    }

}
