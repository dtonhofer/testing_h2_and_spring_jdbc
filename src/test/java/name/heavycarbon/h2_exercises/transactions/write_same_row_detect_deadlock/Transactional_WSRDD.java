package name.heavycarbon.h2_exercises.transactions.write_same_row_detect_deadlock;

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

@Slf4j
@Component
public class Transactional_WSRDD implements MyTransactional {

    @Autowired
    protected Db db;

    @Autowired
    protected SessionManip sm;

    // This is called by a thread which already owns the monitor to AppState.

    @Transactional
    public void runInsideTransaction(@NotNull AgentRunnableBase ar, @NotNull StuffId markerId, @NotNull StuffId collisionId) {
        log.info("{} inside transaction.", ar.agentId);
        sm.setMySessionIsolationLevel(ar.isol);
        boolean interrupted = false; // set when the thread notices it has been interrupted
        try { // to catch InterruptedException
            while (ar.isContinue()) {
                switch (ar.appState.get()) {
                    case 0 -> {
                        log.info("{} in state 0. Writing to row with the marker id.", ar.agentId);
                        db.updateById(markerId, "WRITE FIRST WAS HERE");
                        ar.incState();
                    }
                    case 2 -> {
                        log.info("{} in state 2. Writing to row with the collision id.", ar.agentId);
                        db.updateById(collisionId, "IF YOU SEE THIS, WRITE FIRST WINS");
                        ar.setStop(); // get out of loop
                    }
                    default -> {
                        // wait, relinquishing monitor (but don't wait forever)
                        log.info("{} waiting.", ar.agentId);
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
