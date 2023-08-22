package name.heavycarbon.h2_exercises.transactions.common;

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

// ---
// Common code for an agent that "modifies" the database, i.e. updates, inserts
// or deletes records in "application state 1". It references the class providing
// the methods marked as @Transactional through the "modifierTx" field (actually an interface).
// ---

@Slf4j
public class ModifierRunnable extends AgentRunnableBase {

    // "rowToModifyId" is set by main once known.
    // Set actually before the modifier thread is started.
    // No need to make it "volatile"

    @Setter
    private StuffId setWhatToModify;

    private final TransactionalInterface_OpSwitchOnly modifierTx;
    private final Op op;

    public ModifierRunnable(@NotNull Db db,
                            @NotNull AppState appState,
                            @NotNull AgentId agentId,
                            @NotNull Isol isol,
                            @NotNull TransactionalInterface_OpSwitchOnly modifierTx,
                            @NotNull Op op) {
        super(db, appState, agentId, isol);
        this.modifierTx = modifierTx;
        this.op = op;
    }

    @Override
    public void run() {
        try {
            log.info("{} starting.", agentId);
            boolean interrupted = false; // set when the thread notices it has been interrupted
            try { // to catch InterruptedException
                syncOnAppState();
            } catch (InterruptedException ex) {
                interrupted = true;
            }
            log.info(finalMessage(interrupted));
        } catch (Throwable th) {
            AgentRunnableBase.throwableMessage(log, agentId, th);
        }
    }

    private void syncOnAppState() throws InterruptedException {
        synchronized (appState) {
            log.info("{} in critical section.", agentId);
            while (isContinue()) {
                switch (appState.get()) {
                    case 1 -> {
                        modifierTx.runOpSwitchInsideTransaction(this, op, setWhatToModify);
                        incState();
                        setTerminatedNicely();
                        setStop();
                    }
                    default -> {
                        // wait, relinquishing monitor (but don't wait forever)
                        waitOnAppState();
                    }
                }
            }
        }
    }
}
