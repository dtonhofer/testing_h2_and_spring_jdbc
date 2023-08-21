package name.heavycarbon.h2_exercises.transactions.write_same_row;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentRunnableBase;
import name.heavycarbon.h2_exercises.transactions.common.MyTransactional;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import name.heavycarbon.h2_exercises.transactions.session.SessionManip;
import org.jetbrains.annotations.NotNull;
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

@Slf4j
@Component
public abstract class Transactional_WSR implements MyTransactional {

    @Autowired
    protected Db db;

    @Autowired
    protected SessionManip sm;

    // This is called by a thread which already owns the monitor to AppState.

    @Transactional
    public void runInsideTransaction(@NotNull AgentRunnableBase ar, @NotNull StuffId markerId, @NotNull StuffId collisionId) {
        log.info("{} inside transaction", ar.agentId);
        sm.setMySessionIsolationLevel(ar.isol);
        boolean interrupted = false; // set when the thread notices it has been interrupted
        try { // to catch InterruptedException
            while (ar.isContinue()) {
                // >>>
                writeAccordingToAppState(ar, markerId, collisionId);
                // <<<
            }
        } catch (InterruptedException ex) {
            interrupted = true;
        }
        log.info(ar.finalMessage(interrupted));
    }

    protected abstract void writeAccordingToAppState(@NotNull AgentRunnableBase ar, @NotNull StuffId markerId, @NotNull StuffId collisionId) throws InterruptedException;

}
