package name.heavycarbon.h2_exercises.transactions.nonserializable;

import name.heavycarbon.h2_exercises.transactions.agent.AgentRunnable;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.EnsembleId;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import name.heavycarbon.h2_exercises.transactions.nonserializable.ReadWriteRunnable.Role;
import name.heavycarbon.h2_exercises.transactions.nonserializable.ReadWriteRunnable.LeftAndRightStuffId;
import name.heavycarbon.h2_exercises.transactions.nonserializable.ReadWriteRunnable.Role2Behaviour;
import name.heavycarbon.h2_exercises.transactions.session.SessionManip;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class ReadWriteTransactional {

    @Autowired
    private Db db;

    @Autowired
    private SessionManip sm;

    @Transactional
    public void runInsideTransaction(@NotNull AgentRunnable ar, @NotNull Role role, @NotNull LeftAndRightStuffId ids, Role2Behaviour r2b) {
        assert role != Role.Role2 || r2b != null;
        log.info("{} starting", ar.agentId);
        sm.setMySessionIsolationLevel(ar.isol);
        boolean interrupted = false; // set when the thread notices it has been interrupted
        try { // to catch InterruptedException
            synchronized (ar.appState) {
                log.info("{} in critical section", ar.agentId);
                while (ar.isContinue()) {
                    log.info("{} at top of loop in state {} with role {}", ar.agentId, ar.appState, role);
                    switch (role) {
                        case Role1 -> {
                            role1(ar, ids);
                        }
                        case Role2 -> {
                            role2(ar, ids, r2b);
                        }
                    }
                }
            }
            log.info("{} out of critical section", ar.agentId);
        } catch (InterruptedException ex) {
            interrupted = true;
        }
        log.info(ar.finalMessage(interrupted));
        try {
            int sleep_ms = (int) (Math.random() * 100);
            log.info("{} sleeping for {} ms", ar.agentId, sleep_ms);
            Thread.sleep(sleep_ms);
        } catch (InterruptedException exe) {
            //
        }
    }

    private void read(@NotNull AgentRunnable ar, @NotNull StuffId stuffId, @NotNull String side) {
        log.info("{} reading {}, {}", ar.agentId, side, stuffId);
        db.readById(stuffId);
    }

    private void update(@NotNull AgentRunnable ar, @NotNull StuffId stuffId,  @NotNull String side, @NotNull String text) {
        log.info("{} updating {}, {}", ar.agentId, side, stuffId);
        db.updateById(stuffId, text);
    }

    private void role1(@NotNull AgentRunnable ar, @NotNull LeftAndRightStuffId ids) throws InterruptedException {
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

    private void role2(@NotNull AgentRunnable ar, @NotNull LeftAndRightStuffId ids, @NotNull Role2Behaviour r2b) throws InterruptedException {
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
