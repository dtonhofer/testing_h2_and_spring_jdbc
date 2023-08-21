package name.heavycarbon.h2_exercises.transactions;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.EnsembleId;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import name.heavycarbon.h2_exercises.transactions.session.Isol;
import name.heavycarbon.h2_exercises.transactions.session.SessionManip;
import name.heavycarbon.h2_exercises.transactions.write_same_row.StuffIds;
import name.heavycarbon.h2_exercises.transactions.write_same_row_cannot_get_lock.AgentContainer_WSRCGL;
import name.heavycarbon.h2_exercises.transactions.write_same_row_cannot_get_lock.Transactional_WSR_WriteFirst;
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
@SpringBootTest(classes = {Db.class, SessionManip.class, Transactional_WSR_WriteFirst.class, Transactional_WSR_WriteLast.class})
public class TestWritingToSameRowAndCannotGetLock {

    @Autowired
    private Db db;

    @Autowired
    private Transactional_WSR_WriteFirst writeFirstTx;

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

    private void testWritingSameRow(@NotNull Isol isol) {
        final StuffIds ids = setupDb();
        var ac = new AgentContainer_WSRCGL(db, isol, ids, writeFirstTx, writeLastTx);
        ac.startAll();
        ac.joinAll();
        // Agent "write last" blew up!
        Assertions.assertThat(ac.isAnyThreadTerminatedBadly()).isTrue();
        {
            Optional<Throwable> optt = ac.getWriteFirstRunnable().getCaughtThrowable();
            Assertions.assertThat(optt).isEmpty();
        }
        {
            Optional<Throwable> optt = ac.getWriteLastRunnable().getCaughtThrowable();
            Assertions.assertThat(optt).isPresent();
            // we actually don't see this:
            // "org.springframework.dao.QueryTimeoutException"
            // we see
            // "org.springframework.transaction.TransactionSystemException"
            // apparently because Spring does not manage to translate the
            // "org.h2.jdbc.JdbcSQLTimeoutException"
            // The rollback worked nicely.
            // FIXME How to fix???
            Assertions.assertThat(optt.get()).isInstanceOf(org.springframework.transaction.TransactionSystemException.class);
            Assertions.assertThat(optt.get()).hasMessage("JDBC rollback failed");
        }
        // Agent "write first" wrote its winner message into the row of collision!
        Assertions.assertThat(db.readById(ids.collisionId()).get().payload()).isEqualTo("IF YOU SEE THIS, WRITE FIRST WINS");
        // Agent "write first" wrote its marker message into the row of marking!
        Assertions.assertThat(db.readById(ids.writeFirstMarkId()).get().payload()).isEqualTo("WRITE FIRST WAS HERE");
        // Agent "write last" wrote its marker message into the row of marking, but that change was rolled back
        Assertions.assertThat(db.readById(ids.writeLastMarkId()).get().payload()).isEqualTo("XXX");
    }

    @Test
    public void testWritingSameRow_read_uncomitted() {
        testWritingSameRow(Isol.READ_UNCOMMITTED);
    }

    @Test
    public void testWritingSameRow_read_comitted() {
        testWritingSameRow(Isol.READ_COMMITTED);
    }

    @Test
    public void testWritingSameRow_repeatable_read() {
        testWritingSameRow(Isol.REPEATABLE_READ);
    }

    @Test
    public void testWritingSameRow_serializable() {
        testWritingSameRow(Isol.SERIALIZABLE);
    }

    @Test
    public void testWritingSameRow_snapshot() {
        testWritingSameRow(Isol.SNAPSHOT);
    }
}
