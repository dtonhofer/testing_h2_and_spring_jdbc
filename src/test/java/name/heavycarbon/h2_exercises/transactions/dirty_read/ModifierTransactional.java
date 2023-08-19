package name.heavycarbon.h2_exercises.transactions.dirty_read;

import name.heavycarbon.h2_exercises.transactions.agent.AgentContainerBase.Op;
import name.heavycarbon.h2_exercises.transactions.agent.AgentRunnable;
import name.heavycarbon.h2_exercises.transactions.agent.MyRollbackException;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.EnsembleId;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import name.heavycarbon.h2_exercises.transactions.session.SessionManip;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// ---
// Class holding the methods annotated "@Transactional"
// Spring will weave some code around those methods.
// - This class must be a Component, otherwise @Transactional does nothing.
// - @Transactional annotations must be on the "public" methods otherwise @Transactional does nothing.
// ---

@Slf4j
@Component
public class ModifierTransactional {

    @Autowired
    private Db db;

    @Autowired
    private SessionManip sm;

    @Transactional(rollbackFor = {MyRollbackException.class})
    public void runInsideTransaction(@NotNull AgentRunnable ar, @NotNull Op op, @NotNull StuffId stuffIdOfRowToModify) throws MyRollbackException {
        log.info("{} starting", ar.agentId);
        sm.setMySessionIsolationLevel(ar.isol);
        boolean interrupted = false; // set when the thread notices it has been interrupted
        try { // to catch InterruptedException
            synchronized (ar.appState) {
                log.info("{} in critical section", ar.agentId);
                while (ar.isContinue()) {
                    switch (ar.appState.get()) {
                        case 0 -> {
                            switch (op) {
                                case Insert -> {
                                    db.insert(EnsembleId.Two, "BBB");
                                }
                                case Update -> {
                                    db.updateById(stuffIdOfRowToModify, "XXX");
                                }
                                case Delete -> {
                                    int count = db.deleteById(stuffIdOfRowToModify);
                                    Assertions.assertEquals(count, 1);
                                }
                            }
                            ar.incState();
                        }
                        case 2 -> {
                            // Do nothing except rolling back the transaction
                            // by throwing an exception that is marked as causing rollback
                            // (RuntimeException or Error also cause rollback but
                            // checked exceptions do not by default!)
                            ar.incState();
                            ar.setTerminatedNicely();
                            throw new MyRollbackException("Rolling back any modification");
                        }
                        default -> {
                            // wait, relinquishing monitor (but don't wait forever)
                            ar.waitOnAppState();
                        }
                    }
                }
            }
            log.info("{} out of critical section", ar.agentId);
        } catch (InterruptedException ex) {
            interrupted = true;
        }
        log.info(ar.finalMessage(interrupted));
    }
}
