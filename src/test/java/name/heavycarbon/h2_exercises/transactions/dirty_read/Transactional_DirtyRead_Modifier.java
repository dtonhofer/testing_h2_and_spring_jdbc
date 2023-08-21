package name.heavycarbon.h2_exercises.transactions.dirty_read;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainerBase.Op;
import name.heavycarbon.h2_exercises.transactions.agent.AgentRunnableBase;
import name.heavycarbon.h2_exercises.transactions.agent.MyRollbackException;
import name.heavycarbon.h2_exercises.transactions.common.MyTransactional;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.EnsembleId;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import name.heavycarbon.h2_exercises.transactions.session.SessionManip;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// ---
// Class holding the methods annotated "@Transactional"
//
// Spring will weave some code around those methods using Spring
// Aspect-Oriented-Programming. A look at a stacktrace is of some interest.
//
// => This class must be a @Component, otherwise @Transactional does nothing.
// => @Transactional annotations must be methods called from other objects otherwise
//   @Transactional does nothing. In particular, calling @Transactional methods
//   from other methods of the same class does nothing, as the IDE warns us about.
// ---

// ---
// Note that this class is autowired with references to classes doing database
// queries, and any additional arguments that the transactional method needs are
// passed in the method call.
//
// The transaction level is left unspecified in the annotation and is set explicitly
// inside the transaction. We annotate with "@Transactional" instead of for example
// "@Transactional(isolation = Isolation.READ_UNCOMMITTED)"
// ---

@Slf4j
@Component
public class Transactional_DirtyRead_Modifier implements MyTransactional {

    @Autowired
    private Db db;

    @Autowired
    private SessionManip sm;

    @Transactional(rollbackFor = {MyRollbackException.class})
    public void runStateMachineLoopInsideTransaction(@NotNull AgentRunnableBase ar, @NotNull Op op, @NotNull StuffId rowToModifyId) throws MyRollbackException {
        log.info("{} in transaction.", ar.agentId);
        sm.setMySessionIsolationLevel(ar.isol);
        syncOnAppState(ar, op, rowToModifyId);
    }

    private void syncOnAppState(@NotNull AgentRunnableBase ar, @NotNull Op op, @NotNull StuffId rowToModifyId) throws MyRollbackException {
        synchronized (ar.appState) {
            log.info("{} in critical section.", ar.agentId);
            runStateMachineLoop(ar, op, rowToModifyId);
        }
    }

    private void runStateMachineLoop(@NotNull AgentRunnableBase ar, @NotNull Op op, @NotNull StuffId rowToModifyId) throws MyRollbackException {
        boolean interrupted = false; // set when the thread notices it has been interrupted
        try { // to catch InterruptedException
            while (ar.isContinue()) {
                switch (ar.appState.get()) {
                    case 0 -> {
                        switch (op) {
                            case Insert -> {
                                db.insert(EnsembleId.Two, "BBB");
                            }
                            case Update -> {
                                db.updateById(rowToModifyId, "XXX");
                            }
                            case Delete -> {
                                int count = db.deleteById(rowToModifyId);
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
        } catch (InterruptedException ex) {
            interrupted = true;
        }
        log.info(ar.finalMessage(interrupted));
    }
}