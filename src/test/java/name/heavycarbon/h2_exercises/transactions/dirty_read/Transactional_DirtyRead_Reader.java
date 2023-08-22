package name.heavycarbon.h2_exercises.transactions.dirty_read;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentRunnableBase;
import name.heavycarbon.h2_exercises.transactions.agent.TransactionResult2;
import name.heavycarbon.h2_exercises.transactions.common.*;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.EnsembleId;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import name.heavycarbon.h2_exercises.transactions.session.SessionManip;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
public class Transactional_DirtyRead_Reader implements TransactionalInterface_Reader, MyTransactional {

    @Autowired
    private Db db;

    @Autowired
    private SessionManip sm;

    @Transactional
    public @NotNull Optional<TransactionResult2> runStateMachineLoopInsideTransaction(@NotNull AgentRunnableBase ar, @NotNull WhatToRead whatToRead) {
        log.info("{} in transaction.", ar.agentId);
        sm.setMySessionIsolationLevel(ar.isol);
        return syncOnAppState(ar,whatToRead);
    }

    private @NotNull Optional<TransactionResult2> syncOnAppState(@NotNull AgentRunnableBase ar, @NotNull WhatToRead whatToRead) {
        synchronized (ar.appState) {
            log.info("{} in critical section.", ar.agentId);
            return runStateMachineLoop(ar,whatToRead);
        }
    }

    private @NotNull Optional<TransactionResult2> runStateMachineLoop(@NotNull AgentRunnableBase ar, @NotNull WhatToRead whatToRead) {
        List<Stuff> readResult = null;
        boolean interrupted = false; // set when the thread notices it has been interrupted
        try {
            while (ar.isContinue()) {
                switch (ar.appState.get()) {
                    case 1 -> {
                        // We cover cases "read by id" and "read by (selection on) ensemble-id"
                        // but note that for insertions, only the selection on ensemble-id makes
                        // sense, and we actually always select by ensemble-id.
                        if (whatToRead instanceof WhatToReadById) {
                            StuffId readId = ((WhatToReadById) whatToRead).getId();
                            Optional<Stuff> optStuff = db.readById(readId);
                            readResult = optStuff.map(List::of).orElseGet(ArrayList::new);
                            log.info("{} read by id {}, got {} results.",ar.agentId,readId,readResult.size());
                        }
                        else {
                            assert whatToRead instanceof WhatToReadByEnsemble;
                            EnsembleId ensembleId = ((WhatToReadByEnsemble) whatToRead).getEnsembleId();
                            readResult = db.readEnsemble(ensembleId);
                            log.info("{} read by ensemble {}, got {} results.",ar.agentId,ensembleId,readResult.size());
                        }
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
        } catch (InterruptedException ex) {
            interrupted = true;
        }
        log.info(ar.finalMessage(interrupted));
        assert readResult != null;
        return TransactionResult2.makeOptional(readResult);
    }


}
