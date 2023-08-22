package name.heavycarbon.h2_exercises.transactions.phantom_read;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentRunnableBase;
import name.heavycarbon.h2_exercises.transactions.agent.TransactionResult2;
import name.heavycarbon.h2_exercises.transactions.common.MyTransactional;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalInterface_Reader;
import name.heavycarbon.h2_exercises.transactions.common.WhatToRead;
import name.heavycarbon.h2_exercises.transactions.common.WhatToReadByEnsemble;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.EnsembleId;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import name.heavycarbon.h2_exercises.transactions.session.SessionManip;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
public class Transactional_PhantomRead_Reader implements TransactionalInterface_Reader, MyTransactional {

    @Autowired
    private Db db;

    @Autowired
    private SessionManip sm;

    @Transactional
    public @NotNull Optional<TransactionResult2> runStateMachineLoopInsideTransaction(@NotNull AgentRunnableBase ar, @NotNull WhatToRead whatToRead) {
        log.info("{} inside transaction.", ar.agentId);
        final EnsembleId readThis;
        if (whatToRead instanceof WhatToReadByEnsemble) {
            readThis = ((WhatToReadByEnsemble) whatToRead).getEnsembleId();
        }
        else {
            throw new IllegalArgumentException("Expected WhatToReadByEnsemble instance");
        }
        sm.setMySessionIsolationLevel(ar.isol);
        List<Stuff> readResult1 = null;
        List<Stuff> readResult2 = null;
        boolean interrupted = false; // set when the thread notices it has been interrupted
        try {
            synchronized (ar.appState) {
                log.info("{} in critical section.", ar.agentId);
                while (ar.isContinue()) {
                    switch (ar.appState.get()) {
                        case 0 -> {
                            readResult1 = db.readEnsemble(readThis);
                            ar.incState();
                        }
                        case 2 -> {
                            readResult2 = db.readEnsemble(readThis);
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
        assert readResult1 != null;
        assert readResult2 != null;
        return TransactionResult2.makeOptional(readResult1, readResult2);
    }

}
