package name.heavycarbon.h2_exercises.transactions.write_same_row_cannot_get_lock;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentRunnableBase;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import name.heavycarbon.h2_exercises.transactions.write_same_row.Transactional_WSR;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class Transactional_WSR_WriteFirst extends Transactional_WSR {

    // This is called by a thread which already owns the monitor to AppState.

    protected void writeAccordingToAppState(@NotNull AgentRunnableBase ar, @NotNull StuffId markerId, @NotNull StuffId collisionId) throws InterruptedException {
        switch (ar.appState.get()) {
            case 0 -> {
                log.info("{} in state 0. Writing to row with the marker id.", ar.agentId);
                db.updateById(markerId, "WRITE FIRST WAS HERE");
                ar.incState();
            }
            case 2 -> {
                log.info("{} in state 2. Writing to row with the collision id.", ar.agentId);
                db.updateById(collisionId, "IF YOU SEE THIS, WRITE FIRST WINS");
                ar.incState();
            }
            case 4 -> {
                // Never gets here but ends its loop because the other agent terminated badly
                log.info("{} in state 4.", ar.agentId);
                assert false : "Should not be here!";
                ar.setTerminatedNicely();
                ar.setStop();
            }
            default -> {
                // wait, relinquishing monitor (but don't wait forever)
                log.info("{} waiting.", ar.agentId);
                ar.waitOnAppState();
            }
        }
    }

}
