package name.heavycarbon.h2_exercises.transactions;

import name.heavycarbon.h2_exercises.transactions.agent.AgentContainerBase.Op;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.agent.TransactionResult2;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.EnsembleId;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import name.heavycarbon.h2_exercises.transactions.phantom_read.AgentContainer_PhantomRead;
import name.heavycarbon.h2_exercises.transactions.phantom_read.ModifierTransactional;
import name.heavycarbon.h2_exercises.transactions.phantom_read.ReaderTransactional;
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
// "Phantom Reads" are a "strong" unsoundness that occur in isolation level READ_UNCOMMITTED and lower.
// You need to switch to SERIALIZABLE or SNAPSHOT to get rid of it.
// ---

// Assumption that the classes with the methods marked "Transactional" needs to be injected for a
// valid "transactional proxy" to be slapped around Agent1Transactional.

@Slf4j
@AutoConfigureJdbc
@SpringBootTest(classes = {Db.class, SessionManip.class, ModifierTransactional.class, ReaderTransactional.class})
public class TestPhantomRead {

    private enum Expected {Sound, PhantomRead}

    @Autowired
    private Db db;

    @Autowired
    private ModifierTransactional inserterTx;

    @Autowired
    private ReaderTransactional readerTx;

    private record SetupData(@NotNull List<Stuff> initialRows, @NotNull StuffId modifyThisStuffId) {}

    private @NotNull SetupData setupDb() {
        // The reader predicate is: "belongs to ensemble 2"
        // "initialRow1" is only there for fun
        db.setupDatabase(true);
        db.insert(EnsembleId.One, "AAA");
        db.insert(EnsembleId.Two, "BBB");
        db.insert(EnsembleId.Two, "CCC");
        final List<Stuff> initialRows = db.readEnsemble(EnsembleId.Two); // sorted by field id
        assert  initialRows.size() == 2;
        // this is the one we will delete or update (if that op has been selected)
        final StuffId modifyThisStuffId = initialRows.get(0).id();
        return new SetupData(initialRows, modifyThisStuffId);
    }

    private void testPhantomRead(@NotNull Isol isol, @NotNull Op op, @NotNull Expected expected) {
        final var setupData = setupDb();
        final var ac = new AgentContainer_PhantomRead(db, inserterTx, readerTx, isol, op);
        ac.setStuffIdOfRowToModify(setupData.modifyThisStuffId);
        ac.startAll();
        ac.joinAll();
        // None of the threads should have terminated badly!
        Assertions.assertThat(ac.isAnyThreadTerminatedBadly()).isFalse();
        {
            final Optional<TransactionResult2> optResult = ac.getReaderRunnable().getResult();
            Assertions.assertThat(optResult).isPresent();
            {
                final List<Stuff> readResult1 = optResult.get().readResult1();
                Assertions.assertThat(readResult1).isEqualTo(setupData.initialRows);
            }
            {
                final List<Stuff> readResult2 = optResult.get().readResult2();
                switch (expected) {
                    case PhantomRead -> {
                        readResultMustBeWhatTheModifierDid(op, setupData, readResult2);
                    }
                    case Sound -> {
                        Assertions.assertThat(readResult2).isEqualTo(setupData.initialRows);
                    }
                }
            }
        }
        {
            List<Stuff> finalRead = db.readEnsemble(EnsembleId.Two);
            readResultMustBeWhatTheModifierDid(op, setupData, finalRead);
        }
    }

    private void readResultMustBeWhatTheModifierDid(@NotNull Op op, @NotNull SetupData setupData, @NotNull List<Stuff> readResult) {
        switch (op) {
            case Insert -> {
                Assertions.assertThat(readResult).hasSize(3);
                Assertions.assertThat(setupData.initialRows).hasSize(2); // just a reminder
                Assertions.assertThat(readResult).containsAll(setupData.initialRows);
            }
            case Update, Delete -> {
                Assertions.assertThat(readResult).hasSize(1);
                readResult.stream().noneMatch(stuff -> stuff.id().equals(setupData.modifyThisStuffId));
            }
        }
    }

    // --- INSERTION ---

    @Test
    void testPhantomRead_readUncommitted_insert() {
        testPhantomRead(Isol.READ_UNCOMMITTED, Op.Insert, Expected.PhantomRead);
    }

    @Test
    void testPhantomRead_readCommitted_insert() {
        testPhantomRead(Isol.READ_COMMITTED, Op.Insert, Expected.PhantomRead);
    }

    @Test
    void testPhantomRead_repeatableRead_insert() {
        testPhantomRead(Isol.REPEATABLE_READ, Op.Insert, Expected.Sound);
    }

    @Test
    void testPhantomRead_serializable_insert() {
        testPhantomRead(Isol.SERIALIZABLE, Op.Insert, Expected.Sound);
    }

    @Test
    void testPhantomRead_snapshot_insert() {
        testPhantomRead(Isol.SNAPSHOT, Op.Insert, Expected.Sound);
    }

    // --- UPDATE ---

    @Test
    void testPhantomRead_readUncommitted_update() {
        testPhantomRead(Isol.READ_UNCOMMITTED, Op.Update, Expected.PhantomRead);
    }

    @Test
    void testPhantomRead_readCommitted_update() {
        testPhantomRead(Isol.READ_COMMITTED, Op.Update, Expected.PhantomRead);
    }

    @Test
    void testPhantomRead_repeatableRead_update() {
        testPhantomRead(Isol.REPEATABLE_READ, Op.Update, Expected.Sound);
    }

    @Test
    void testPhantomRead_serializable_update() {
        testPhantomRead(Isol.SERIALIZABLE, Op.Update, Expected.Sound);
    }

    @Test
    void testPhantomRead_snapshot_update() {
        testPhantomRead(Isol.SNAPSHOT, Op.Update, Expected.Sound);
    }

    // --- DELETE ---

    @Test
    void testPhantomRead_readUncommitted_delete() {
        testPhantomRead(Isol.READ_UNCOMMITTED, Op.Delete, Expected.PhantomRead);
    }

    @Test
    void testPhantomRead_readCommitted_delete() {
        testPhantomRead(Isol.READ_COMMITTED, Op.Delete, Expected.PhantomRead);
    }

    @Test
    void testPhantomRead_repeatableRead_delete() {
        testPhantomRead(Isol.REPEATABLE_READ, Op.Delete, Expected.Sound);
    }

    @Test
    void testPhantomRead_serializable_delete() {
        testPhantomRead(Isol.SERIALIZABLE, Op.Delete, Expected.Sound);
    }

    @Test
    void testPhantomRead_snapshot_delete() {
        testPhantomRead(Isol.SNAPSHOT, Op.Delete, Expected.Sound);
    }

}