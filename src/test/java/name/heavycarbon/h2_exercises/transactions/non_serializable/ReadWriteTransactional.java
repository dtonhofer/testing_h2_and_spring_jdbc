package name.heavycarbon.h2_exercises.transactions.non_serializable;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentRunnableBase;
import name.heavycarbon.h2_exercises.transactions.common.MyTransactional;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import name.heavycarbon.h2_exercises.transactions.non_serializable.ReadWriteRunnable.Role;
import name.heavycarbon.h2_exercises.transactions.non_serializable.ReadWriteRunnable.Role2Behaviour;
import name.heavycarbon.h2_exercises.transactions.session.SessionManip;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
public class ReadWriteTransactional implements MyTransactional {

    @Autowired
    private Db db;

    @Autowired
    private SessionManip sm;

    @Transactional
    public void runStateMachineLoopInsideTransaction(@NotNull AgentRunnableBase ar, @NotNull Role role, @NotNull LeftAndRightStuffId ids, Optional<Role2Behaviour> r2b) {
        assert role != Role.Role2 || r2b.isPresent();
        log.info("{} inside transaction.", ar.agentId);
        sm.setMySessionIsolationLevel(ar.isol);
        syncOnAppState(ar, role, ids, r2b);
    }

    private void syncOnAppState(@NotNull AgentRunnableBase ar, @NotNull Role role, @NotNull LeftAndRightStuffId ids, Optional<Role2Behaviour> r2b) {
        synchronized (ar.appState) {
            log.info("{} in critical section.", ar.agentId);
            runStateMachineLoop(ar, role, ids, r2b);
        }
    }

    private void runStateMachineLoop(@NotNull AgentRunnableBase ar, @NotNull Role role, @NotNull LeftAndRightStuffId ids, Optional<Role2Behaviour> r2b) {
        boolean interrupted = false; // set when the thread notices it has been interrupted
        try { // to catch InterruptedException
            while (ar.isContinue()) {
                log.info("{} at top of loop in state {} with role {}.", ar.agentId, ar.appState, role);
                switch (role) {
                    case Role1 -> {
                        role1(ar, ids);
                    }
                    case Role2 -> {
                        role2(ar, ids, r2b.get());
                    }
                }
            }
        } catch (InterruptedException ex) {
            interrupted = true;
        }
        interrupted = interrupted || sleepRandomly(ar);
        log.info(ar.finalMessage(interrupted));
    }

    private boolean sleepRandomly(@NotNull AgentRunnableBase ar) {
        try {
            int sleep_ms = (int) (Math.random() * 100);
            log.info("{} sleeping for {} ms", ar.agentId, sleep_ms);
            Thread.sleep(sleep_ms);
            return false;
        } catch (InterruptedException exe) {
            return true;
        }
    }

    private void read(@NotNull AgentRunnableBase ar, @NotNull StuffId stuffId, @NotNull String side) {
        log.info("{} reading {}, {}", ar.agentId, side, stuffId);
        db.readById(stuffId);
    }

    private void update(@NotNull AgentRunnableBase ar, @NotNull StuffId stuffId, @NotNull String side, @NotNull String text) {
        log.info("{} updating {}, {}", ar.agentId, side, stuffId);
        db.updateById(stuffId, text);
    }

    private void role1(@NotNull AgentRunnableBase ar, @NotNull LeftAndRightStuffId ids) throws InterruptedException {
        switch (ar.appState.get()) {
            case 0 -> {
                db.updateById(ids.role1Id(), "ROLE 1 ACTIVE");
                ar.incState();
            }
            case 2 -> {
                read(ar, ids.rightId(), "right");
                ar.incState();
            }
            case 4 -> {
                update(ar, ids.leftId(), "left", "ROLE 1");
                ar.incState();
                ar.setTerminatedNicely();
                ar.setStop();
            }
            default -> {
                ar.waitOnAppState();
            }
        }
    }

    private void role2(@NotNull AgentRunnableBase ar, @NotNull LeftAndRightStuffId ids, @NotNull Role2Behaviour r2b) throws InterruptedException {
        switch (ar.appState.get()) {
            case 1 -> {
                db.updateById(ids.role2Id(), "ROLE 2 ACTIVE");
                ar.incState();
            }
            case 3 -> {
                switch (r2b) {
                    case Alfa -> read(ar, ids.rightId(), "right"); // OK
                    case Bravo -> read(ar, ids.rightId(), "right");
                    case Charlie -> update(ar, ids.leftId(), "left", "ROLE 2");
                    case Delta -> update(ar, ids.rightId(), "right", "ROLE 2");
                }
                ar.incState();
            }
            case 5 -> {
                switch (r2b) {
                    case Alfa -> update(ar, ids.leftId(), "left", "ROLE 2"); // OK
                    case Bravo -> update(ar, ids.leftId(), "left", "ROLE 2");
                    case Charlie -> read(ar, ids.rightId(), "right");
                    case Delta -> read(ar, ids.leftId(), "left");
                }
                ar.setTerminatedNicely();
                ar.setStop();
            }
            default -> {
                ar.waitOnAppState();
            }
        }
    }

}
