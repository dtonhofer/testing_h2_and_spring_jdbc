package name.heavycarbon.h2_exercises.transactions;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.EnsembleId;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import name.heavycarbon.h2_exercises.transactions.session.Isol;
import name.heavycarbon.h2_exercises.transactions.session.SessionManip;
import name.heavycarbon.h2_exercises.transactions.write_same_row.StuffIds;
import name.heavycarbon.h2_exercises.transactions.write_same_row_detect_deadlock.AgentContainer_WSRDD;
import name.heavycarbon.h2_exercises.transactions.write_same_row_detect_deadlock.Transactional_WSRDD;
import name.heavycarbon.h2_exercises.transactions.write_same_row_cannot_get_lock.Transactional_WSR_WriteLast;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureJdbc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

@Slf4j
@AutoConfigureJdbc
@SpringBootTest(classes = {Db.class, SessionManip.class, Transactional_WSRDD.class, Transactional_WSR_WriteLast.class})
public class TestWritingToSameRowAndDetectDeadlock {

    @Autowired
    private Db db;

    @Autowired
    private Transactional_WSRDD writeFirstTx;

    @Autowired
    private Transactional_WSR_WriteLast writeLastTx;

    private @NotNull StuffIds setupDb() {
        db.setupDatabase(true);
        // "write first" agent updates this first (to see whether its transaction rolls back)
        StuffId writeFirstMarkId = db.insert(EnsembleId.Two, "XXX");
        // "write last" agent updates this first (to see whether its transaction rolls back)
        StuffId writeLastMarkId = db.insert(EnsembleId.Two, "XXX");
        // then both agents try to update this
        StuffId collisionId = db.insert(EnsembleId.Two, "XXX");
        return new StuffIds(writeFirstMarkId, writeLastMarkId, collisionId);
    }

    enum Expecting {Success, Failure}

    private void testWritingSameRow(@NotNull Isol isol, @NotNull Expecting expecting) {
        final StuffIds ids = setupDb();
        var ac = new AgentContainer_WSRDD(db, isol, ids, writeFirstTx, writeLastTx);
        ac.startAll();
        ac.joinAll();
        switch (expecting) {
            case Failure -> {         // Agent "write last" blew up!
                Assertions.assertThat(ac.isAnyThreadTerminatedBadly()).isTrue();
                {
                    Optional<Throwable> optt = ac.getWriteFirstRunnable().getCaughtThrowable();
                    Assertions.assertThat(optt).isEmpty();
                }
                {
                    Optional<Throwable> optt = ac.getWriteLastRunnable().getCaughtThrowable();
                    Assertions.assertThat(optt).isPresent();
                    Assertions.assertThat(optt.get()).isInstanceOf(org.springframework.dao.CannotAcquireLockException.class);
                    Assertions.assertThat(optt.get()).hasMessageContaining("Deadlock detected. The current transaction was rolled back");
                }
                // Agent "write first" wrote its winner message into the row of collision!
                Assertions.assertThat(db.readById(ids.collisionId()).get().payload()).isEqualTo("IF YOU SEE THIS, WRITE FIRST WINS");
                // Agent "write first" wrote its marker message into the row of marking!
                Assertions.assertThat(db.readById(ids.writeFirstMarkId()).get().payload()).isEqualTo("WRITE FIRST WAS HERE");
                // Agent "write last" wrote its marker message into the row of marking, but that change was rolled back
                Assertions.assertThat(db.readById(ids.writeLastMarkId()).get().payload()).isEqualTo("XXX");
            }
            case Success -> {
                Assertions.assertThat(ac.isAnyThreadTerminatedBadly()).isFalse();
                Assertions.assertThat(db.readById(ids.collisionId()).get().payload()).isEqualTo("IF YOU SEE THIS, WRITE LAST WINS");
                // Agent "write first" wrote its marker message into the row of marking!
                Assertions.assertThat(db.readById(ids.writeFirstMarkId()).get().payload()).isEqualTo("WRITE FIRST WAS HERE");
                // Agent "write last" wrote its marker message into the row of marking, but that change was rolled back
                Assertions.assertThat(db.readById(ids.writeLastMarkId()).get().payload()).isEqualTo("WRITE LAST WAS HERE");
            }
        }
    }

    @Test
    public void testWritingSameRow_read_uncomitted() {
        testWritingSameRow(Isol.READ_UNCOMMITTED, Expecting.Success);
    }

    @Test
    public void testWritingSameRow_read_comitted() {
        testWritingSameRow(Isol.READ_COMMITTED, Expecting.Success);
    }

    @Test
    public void testWritingSameRow_repeatable_read() {
        testWritingSameRow(Isol.REPEATABLE_READ, Expecting.Failure);
    }

    @Test
    public void testWritingSameRow_serializable() {
        testWritingSameRow(Isol.SERIALIZABLE, Expecting.Failure);
    }

    @Test
    public void testWritingSameRow_snapshot() {
        testWritingSameRow(Isol.SNAPSHOT, Expecting.Failure);
    }
}
