package name.heavycarbon.h2_exercises.transactions.dirty_read;

import name.heavycarbon.h2_exercises.transactions.agent.AgentRunnable;
import name.heavycarbon.h2_exercises.transactions.agent.TransactionResult1;
import name.heavycarbon.h2_exercises.transactions.agent.TransactionResult2;
import name.heavycarbon.h2_exercises.transactions.common.ReaderTransactionalInterface;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.EnsembleId;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import name.heavycarbon.h2_exercises.transactions.session.SessionManip;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

// ---
// Class holding the methods annotated "@Transactional"
// Spring will weave some code around those methods.
// - This class must be a Component, otherwise @Transactional does nothing.
// - @Transactional annotations must be on the "public" methods otherwise @Transactional does nothing.
// ---

// This class being "autowired", pass any variable values through method calls
// rather than the constructor.

// The transaction level is left unspecified in the annotation and set explicitly
// inside the transaction.
// @Transactional
// instead of
// @Transactional(isolation = Isolation.READ_UNCOMMITTED)

@Slf4j
@Component
public class ReaderTransactional implements ReaderTransactionalInterface {

    @Autowired
    private Db db;

    @Autowired
    private SessionManip sm;

    @Transactional
    public @NotNull Optional<TransactionResult2> runInsideTransaction(@NotNull AgentRunnable ar) {
        List<Stuff> readResult = null;
        log.info("{} starting", ar.agentId);
        sm.setMySessionIsolationLevel(ar.isol);
        boolean interrupted = false; // set when the thread notices it has been interrupted
        try {
            synchronized (ar.appState) {
                log.info("{} in critical section", ar.agentId);
                while (ar.isContinue()) {
                    switch (ar.appState.get()) {
                        case 1 -> {
                            readResult = db.readEnsemble(EnsembleId.Two);
                            ar.incState();
                        }
                        case 3 -> {
                            ar.setTerminatedNicely();
                            ar.setStop();
                        }
                        default -> {
                            // wait, relinquishing monitor (but don't wait forever)
                            ar.waitOnAppState();
                        }
                    }
                }
            }
        } catch (InterruptedException ex) {
            interrupted = true;
        }
        log.info(ar.finalMessage(interrupted));
        if (readResult != null) {
            return Optional.of(new TransactionResult2(readResult, null));
        } else {
            return Optional.empty();
        }
    }
}
