package name.heavycarbon.h2_exercises.transactions;

import name.heavycarbon.h2_exercises.transactions.agent.AgentContainerBase.Op;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.agent.TransactionResult2;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.EnsembleId;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import name.heavycarbon.h2_exercises.transactions.dirty_read.AgentContainer_DirtyRead;
import name.heavycarbon.h2_exercises.transactions.dirty_read.ModifierTransactional;
import name.heavycarbon.h2_exercises.transactions.dirty_read.ReaderTransactional;
import name.heavycarbon.h2_exercises.transactions.session.Isol;
import name.heavycarbon.h2_exercises.transactions.session.SessionManip;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureJdbc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

// ---
// "Dirty Reads" are a weak unsoundness that only occur in the lowest isolation level READ_UNCOMMITTED
// i.e. if there are no transactions at all.
// ---

// Assumption that the classes with the methods marked "Transactional" needs to be injected for a
// valid "transactional proxy" to be slapped around Agent1Transactional.

@Slf4j
@AutoConfigureJdbc
@SpringBootTest(classes = {Db.class, SessionManip.class, ModifierTransactional.class, ReaderTransactional.class})
public class TestDirtyReads {

    private enum Expected {Sound, DirtyRead}

    @Autowired
    private Db db;

    @Autowired
    private ModifierTransactional inserterTx;

    @Autowired
    private ReaderTransactional readerTx;

    private record SetupData(@NotNull List<Stuff> initialRows, @NotNull StuffId modifyThisStuffId) {
    }

    private @NotNull SetupData setupDb() {
        db.setupDatabase(true);
        db.insert(EnsembleId.Two, "AAA");
        final List<Stuff> initialRows = db.readEnsemble(EnsembleId.Two);
        assert initialRows.size() == 1;
        final StuffId modifyThisStuffId = initialRows.get(0).id();
        return new SetupData(initialRows, modifyThisStuffId);
    }

    private void testDirtyRead(@NotNull Isol isol, @NotNull Op op, @NotNull Expected expected) {
        final SetupData setupData = setupDb();
        final List<Stuff> initialRows = setupData.initialRows();
        final Stuff initialRow = initialRows.get(0);
        final var ac = new AgentContainer_DirtyRead(db, inserterTx, readerTx, isol, op);
        ac.setStuffIdOfRowToModify(setupData.modifyThisStuffId());
        ac.startAll();
        ac.joinAll();
        // None of the threads should have terminated badly!
        Assertions.assertThat(ac.isAnyThreadTerminatedBadly()).isFalse();
        final Optional<TransactionResult2> optResult = ac.getReaderRunnable().getResult();
        // There should be a nonempty transaction result!
        Assertions.assertThat(optResult).isPresent();
        final List<Stuff> readResult = optResult.get().readResult1();
        switch (expected) {
            case DirtyRead -> {
                switch (op) {
                    case Insert -> {
                        Assertions.assertThat(readResult).hasSize(2);
                        Assertions.assertThat(readResult).contains(initialRow);
                    }
                    case Update -> {
                        Assertions.assertThat(readResult).hasSize(1);
                        final Stuff updatedStuff = initialRow.with("XXX");
                        final Stuff uniqueReadResult = readResult.get(0);
                        Assertions.assertThat(uniqueReadResult).isEqualTo(updatedStuff);
                    }
                    case Delete -> {
                        Assertions.assertThat(readResult).isEmpty();
                    }
                }
            }
            case Sound -> {
                Assertions.assertThat(readResult).isEqualTo(initialRows);
            }
        }
        // When all is said and that, there is only the original record.
        // The haunting of the database left no traces.
        Assertions.assertThat(db.readEnsemble(EnsembleId.Two)).isEqualTo(initialRows);
    }

    // --- INSERTION ---

    @Test
    void testDirtyRead_readUncommitted_insert() {
        testDirtyRead(Isol.READ_UNCOMMITTED, Op.Insert, Expected.DirtyRead);
    }

    @Test
    void testDirtyRead_readCommitted_insert() {
        testDirtyRead(Isol.READ_COMMITTED, Op.Insert, Expected.Sound);
    }

    @Test
    void testDirtyRead_repeatableRead_insert() {
        testDirtyRead(Isol.REPEATABLE_READ, Op.Insert, Expected.Sound);
    }

    @Test
    void testDirtyRead_serializable_insert() {
        testDirtyRead(Isol.SERIALIZABLE, Op.Insert, Expected.Sound);
    }

    @Test
    void testDirtyRead_snapshot_insert() {
        testDirtyRead(Isol.SNAPSHOT, Op.Insert, Expected.Sound);
    }

    // --- UPDATE ---

    @Test
    void testDirtyRead_readUncommitted_update() {
        testDirtyRead(Isol.READ_UNCOMMITTED, Op.Update, Expected.DirtyRead);
    }

    @Test
    void testDirtyRead_readCommitted_update() {
        testDirtyRead(Isol.READ_COMMITTED, Op.Update, Expected.Sound);
    }

    @Test
    void testDirtyRead_repeatableRead_update() {
        testDirtyRead(Isol.REPEATABLE_READ, Op.Update, Expected.Sound);
    }

    @Test
    void testDirtyRead_serializable_update() {
        testDirtyRead(Isol.SERIALIZABLE, Op.Update, Expected.Sound);
    }

    @Test
    void testDirtyRead_snapshot_update() {
        testDirtyRead(Isol.SNAPSHOT, Op.Update, Expected.Sound);
    }

    // --- DELETE ---

    @Test
    void testDirtyRead_readUncommitted_delete() {
        testDirtyRead(Isol.READ_UNCOMMITTED, Op.Delete, Expected.DirtyRead);
    }

    @Test
    void testDirtyRead_readCommitted_delete() {
        testDirtyRead(Isol.READ_COMMITTED, Op.Delete, Expected.Sound);
    }

    @Test
    void testDirtyRead_repeatableRead_delete() {
        testDirtyRead(Isol.REPEATABLE_READ, Op.Delete, Expected.Sound);
    }

    @Test
    void testDirtyRead_serializable_delete() {
        testDirtyRead(Isol.SERIALIZABLE, Op.Delete, Expected.Sound);
    }

    @Test
    void testDirtyRead_snapshot_delete() {
        testDirtyRead(Isol.SNAPSHOT, Op.Delete, Expected.Sound);
    }
}
