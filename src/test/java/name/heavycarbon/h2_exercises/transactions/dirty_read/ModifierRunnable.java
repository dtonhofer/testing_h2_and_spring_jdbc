package name.heavycarbon.h2_exercises.transactions.dirty_read;

import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AgentRunnable;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainerBase.Op;
import name.heavycarbon.h2_exercises.transactions.session.Isol;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class ModifierRunnable extends AgentRunnable {

    private final ModifierTransactional modifierTx;
    private final Op op;

    // To be set by main when known. Set actually before the modifier thread is started.
    // No need to make it "volatile"

    @Setter
    private StuffId stuffIdOfRowToModify;

    public ModifierRunnable(@NotNull Db db,
                            @NotNull AppState appState,
                            @NotNull AgentId agentId,
                            @NotNull Isol isol,
                            @NotNull ModifierTransactional modifierTx,
                            @NotNull Op op) {
        super(db, appState, agentId, isol);
        this.modifierTx = modifierTx;
        this.op = op;
    }

    @Override
    public void run() {
        try {
            // this call is expected to throw MyRollbackException
            modifierTx.runInsideTransaction(this, op, stuffIdOfRowToModify);
        } catch (Throwable th) {
            AgentRunnable.throwableMessage(log, agentId, th);
        }
    }

}
