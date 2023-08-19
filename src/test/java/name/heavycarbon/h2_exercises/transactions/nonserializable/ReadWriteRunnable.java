package name.heavycarbon.h2_exercises.transactions.nonserializable;

import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AgentRunnable;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import name.heavycarbon.h2_exercises.transactions.session.Isol;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class ReadWriteRunnable extends AgentRunnable {

    private final ReadWriteTransactional tx;
    private final Role role;
    private final LeftAndRightStuffId ids;
    private final Role2Behaviour r2b;

    public enum Role {Role1, Role2}

    public enum Role2Behaviour {Alfa, Bravo, Charlie, Delta}

    public record LeftAndRightStuffId(@NotNull StuffId leftId, @NotNull StuffId rightId, @NotNull StuffId role1Id, @NotNull StuffId role2Id) {
    }

    public ReadWriteRunnable(@NotNull Db db,
                             @NotNull AppState appState,
                             @NotNull AgentId agentId,
                             @NotNull Isol isol,
                             @NotNull Role role,
                             @NotNull ReadWriteTransactional tx,
                             @NotNull LeftAndRightStuffId ids,
                             Role2Behaviour r2b) {
        super(db, appState, agentId, isol);
        assert role != Role.Role2 || r2b != null;
        this.tx = tx;
        this.role = role;
        this.ids = ids;
        this.r2b = r2b;
    }

    @Override
    public void run() {
        try {
            log.info("{} entering transaction", agentId);
            tx.runInsideTransaction(this, role, ids, r2b);
            log.info("{} out of transaction", agentId);
        } catch (Throwable th) {
            AgentRunnable.throwableMessage(log, agentId, th);
        }
    }
}
