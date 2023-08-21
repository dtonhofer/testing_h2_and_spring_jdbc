package name.heavycarbon.h2_exercises.transactions.phantom_read;

import name.heavycarbon.h2_exercises.transactions.agent.AgentRunnableBase;
import name.heavycarbon.h2_exercises.transactions.common.MyTransactional;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainerBase.Op;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalInterface_OpSwitchOnly;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.EnsembleId;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import name.heavycarbon.h2_exercises.transactions.session.SessionManip;
import lombok.extern.slf4j.Slf4j;
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
public class Transactional_PhantomRead_Modifier implements TransactionalInterface_OpSwitchOnly, MyTransactional {

    @Autowired
    private Db db;

    @Autowired
    private SessionManip sm;

    @Transactional
    public void runOpSwitchInsideTransaction(@NotNull AgentRunnableBase ar, @NotNull Op op, @NotNull StuffId rowToModifyId) {
        log.info("{} inside transaction.", ar.agentId);
        sm.setMySessionIsolationLevel(ar.isol);
        switch (op) {
            case Insert -> {
                // add a record matching the reader's predicate (must be "ensemble 2")
                db.insert(EnsembleId.Two, "YYY");
            }
            case Update -> {
                // Update a record matching the reader's predicate
                // so that it *no longer matches* (move from "ensemble 2" to "ensemble 1")
                int count = db.updateById(rowToModifyId, EnsembleId.One);
                assert count == 1;
            }
            case Delete -> {
                // Delete a record matching the reader's predicate
                // (must have been "ensemble 2")
                int count = db.deleteById(rowToModifyId);
                assert count == 1;
            }
        }
    }

}
