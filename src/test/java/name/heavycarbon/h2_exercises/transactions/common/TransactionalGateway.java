package name.heavycarbon.h2_exercises.transactions.common;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.MyRollbackException;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import name.heavycarbon.h2_exercises.transactions.db.SessionManip;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// ---
// Class holding a single method annotated "@Transactional". It sets
// the isolation level then calls back the Runnable (a parameterless
// method that belongs to some instance) to effect whatever shall be
// done inside the transaction.
// ---

// ---
// Spring will weave some code around those methods using Spring
// Aspect-Oriented-Programming. A look at a stacktrace is of some interest!
//
// => This class must be a "@Component", otherwise "@Transactional" does nothing.
// => "@Transactional" annotations must be on methods called "from other classes"
//    otherwise "@Transactional" does nothing. In particular, calling "@Transactional"
//    methods from other methods of the same class does nothing. The IDE warns us about
//    that.
// ---

// ---
// Note that this class is autowired with references to instances of classes fronting
// database queries (in this case, just "SessionManip").
// ---

// ---
// The transaction level is left unspecified in the annotation and is set explicitly
// inside the transaction. We annotate with "@Transactional" instead of for example
// "@Transactional(isolation = Isolation.READ_UNCOMMITTED)"
// ---

@Slf4j
@Component
public class TransactionalGateway {

    @Autowired
    private SessionManip sm;

    // ---
    // The agent that modifies the database will call this method, passing it a
    // Runnable which holds the code that shall be run inside the transaction.
    // This will be a method of the call. For additional elegance we will have
    // Java create the Runnable by using "functional interface" notation.
    // ---

    @Transactional(rollbackFor = { MyRollbackException.class, InterruptedException.class })
    public void wrapIntoTransaction(@NotNull ThrowingRunnable runnable, @NotNull AgentId agentId, @NotNull Isol isol) throws MyRollbackException, InterruptedException {
        log.info("'{}' in transaction. Setting to level {}", agentId, isol);
        sm.setMySessionIsolationLevel(isol);
        runnable.run();
    }

}

