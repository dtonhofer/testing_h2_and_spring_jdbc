package name.heavycarbon.h2_exercises.transactions.common;

import name.heavycarbon.h2_exercises.transactions.agent.AgentContainerBase.Op;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AgentRunnable;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import name.heavycarbon.h2_exercises.transactions.session.Isol;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class ModifierRunnable extends AgentRunnable {

    private final ModifierTransactionalInterface modifierTxInterface;
    private final Op op;

    // To be set by main when known. Set actually before the modifier thread is started.
    // No need to make it "volatile"

    @Setter
    private StuffId stuffIdOfRowToModify;

    public ModifierRunnable(@NotNull Db db,
                            @NotNull AppState appState,
                            @NotNull AgentId agentId,
                            @NotNull Isol isol,
                            @NotNull ModifierTransactionalInterface modifierTxInterface,
                            @NotNull Op op) {
        super(db, appState, agentId, isol);
        this.modifierTxInterface = modifierTxInterface;
        this.op = op;
    }

    @Override
    public void run() {
        try {
            log.info("{} starting", agentId);
            boolean interrupted = false; // set when the thread notices it has been interrupted
            try { // to catch InterruptedException
                synchronized (appState) {
                    log.info("{} in critical section", agentId);
                    while (isContinue()) {
                        switch (appState.get()) {
                            case 1 -> {
                                modifierTxInterface.runInsideTransaction(op, isol, stuffIdOfRowToModify);
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
                log.info("{} out of critical section", agentId);
            } catch (InterruptedException ex) {
                interrupted = true;
            }
            log.info(finalMessage(interrupted));
        } catch (Throwable th) {
            AgentRunnable.throwableMessage(log, agentId, th);
        }
    }

}
