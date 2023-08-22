package name.heavycarbon.h2_exercises.transactions.dirty_read;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainerBase.Op;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AgentRunnableBase;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import name.heavycarbon.h2_exercises.transactions.session.Isol;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class ModifierRunnable extends AgentRunnableBase {

    // "rowToModifyId" is set by main once known.
    // Set actually before the modifier thread is started.
    // No need to make it "volatile"

    @Setter
    private StuffId whatToModify;

    private final Transactional_DirtyRead_Modifier modifierTx;
    private final Op op;

    public ModifierRunnable(@NotNull Db db,
                            @NotNull AppState appState,
                            @NotNull AgentId agentId,
                            @NotNull Isol isol,
                            @NotNull Transactional_DirtyRead_Modifier modifierTx,
                            @NotNull Op op) {
        super(db, appState, agentId, isol);
        this.modifierTx = modifierTx;
        this.op = op;
    }

    @Override
    public void run() {
        try {
            // this call is expected to throw MyRollbackException
            modifierTx.runStateMachineLoopInsideTransaction(this, op, whatToModify);
        } catch (Throwable th) {
            AgentRunnableBase.throwableMessage(log, agentId, th);
        }
    }

}
