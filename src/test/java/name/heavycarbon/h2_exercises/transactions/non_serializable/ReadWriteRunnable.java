package name.heavycarbon.h2_exercises.transactions.non_serializable;

import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AgentRunnableBase;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.session.Isol;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@Slf4j
public class ReadWriteRunnable extends AgentRunnableBase {

    public enum Role {Role1, Role2}

    public enum Role2Behaviour {Alfa, Bravo, Charlie, Delta}

    private final ReadWriteTransactional tx;
    private final Role role;
    private final LeftAndRightStuffId ids;
    private final Optional<Role2Behaviour> r2b;

    public ReadWriteRunnable(@NotNull Db db,
                             @NotNull AppState appState,
                             @NotNull AgentId agentId,
                             @NotNull Isol isol,
                             @NotNull Role role,
                             @NotNull ReadWriteTransactional tx,
                             @NotNull LeftAndRightStuffId ids,
                             @NotNull Optional<Role2Behaviour> r2b) {
        super(db, appState, agentId, isol);
        assert role != Role.Role2 || r2b.isEmpty();
        this.tx = tx;
        this.role = role;
        this.ids = ids;
        this.r2b = r2b;
    }

    @Override
    public void run() {
        try {
            log.info("{} entering transaction", agentId);
            tx.runStateMachineLoopInsideTransaction(this, role, ids, r2b);
            log.info("{} out of transaction", agentId);
        } catch (Throwable th) {
            AgentRunnableBase.throwableMessage(log, agentId, th);
        }
    }
}
