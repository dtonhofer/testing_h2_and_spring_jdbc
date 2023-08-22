package name.heavycarbon.h2_exercises.transactions;

import name.heavycarbon.h2_exercises.transactions.agent.AgentContainerBase.Op;
import name.heavycarbon.h2_exercises.transactions.agent.TransactionResult2;
import name.heavycarbon.h2_exercises.transactions.common.WhatToReadByEnsemble;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.EnsembleId;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import name.heavycarbon.h2_exercises.transactions.dirty_read.AgentContainer_DirtyRead;
import name.heavycarbon.h2_exercises.transactions.dirty_read.Transactional_DirtyRead_Modifier;
import name.heavycarbon.h2_exercises.transactions.dirty_read.Transactional_DirtyRead_Reader;
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
// Try to elicit a "dirty read" whereby transaction T1
// reads data written but not yet committed by transaction T2. This crass unsoundness is
// supposed to go away at transaction level `READ COMMITTED` and above, and it does.
// ---

// Assumption that the classes with the methods marked "Transactional" needs to be injected for a
// valid "transactional proxy" to be slapped around them.

@Slf4j
@AutoConfigureJdbc
@SpringBootTest(classes = {Db.class, SessionManip.class, Transactional_DirtyRead_Modifier.class, Transactional_DirtyRead_Reader.class})
public class TestElicitingDirtyReads {

    private enum Expected {Soundness, DirtyRead}

    @Autowired
    private Db db;

    @Autowired
    private Transactional_DirtyRead_Modifier inserterTx;

    @Autowired
    private Transactional_DirtyRead_Reader readerTx;

    // the value of the second column, arbitrary

    private static final EnsembleId theEnsembleId = EnsembleId.Two;

    private @NotNull Stuff setupDbReturnInitialRow() {
        db.setupDatabase(true);
        db.insert(theEnsembleId, "AAA");
        final List<Stuff> initialRows = db.readEnsemble(theEnsembleId);
        assert initialRows.size() == 1;
        return initialRows.get(0);
    }

    private void testDirtyRead(@NotNull Isol isol, @NotNull Op op, @NotNull Expected expected) {
        final Stuff initialRow = setupDbReturnInitialRow();
        final var ac = new AgentContainer_DirtyRead(db, inserterTx, readerTx, isol, op);
        ac.setWhatToModify(initialRow.id());
        ac.setWhatToRead(new WhatToReadByEnsemble(theEnsembleId));
        // Let's run it
        ac.startAll();
        ac.joinAll();
        // None of the threads should have terminated badly!
        Assertions.assertThat(ac.isAnyThreadTerminatedBadly()).isFalse();
        final Optional<TransactionResult2> optResult = ac.getReaderRunnable().getResult();
        // There should be a nonempty transaction result!
        Assertions.assertThat(optResult).isPresent();
        // The reader agent, just reads once, so only readResult1 is available
        final List<Stuff> readResult = optResult.get().readResult1();
        switch (expected) {
            case DirtyRead -> {
                switch (op) {
                    case Insert -> {
                        // The reader erroneously saw an additional row
                        // This only makes sense if you read "by ensemble" not "by id"
                        Assertions.assertThat(readResult).hasSize(2);
                        Assertions.assertThat(readResult).contains(initialRow);
                    }
                    case Update -> {
                        // The reader erroneously sees an updated row
                        Assertions.assertThat(readResult).hasSize(1);
                        final Stuff rowSeen = readResult.get(0);
                        final Stuff rowExpectedBecauseUpdated = initialRow.with("XXX");
                        Assertions.assertThat(rowSeen).isEqualTo(rowExpectedBecauseUpdated);
                    }
                    case Delete -> {
                        // The reader erroneously saw that the row was deleted
                        Assertions.assertThat(readResult).isEmpty();
                    }
                }
            }
            case Soundness -> {
                // The reader agent always sees the initial row
                Assertions.assertThat(readResult).isEqualTo(List.of(initialRow));
            }
        }
        // When all is said and done, there is only the original row because the writer rolled back
        {
            List<Stuff> currentDbRows = db.readEnsemble(theEnsembleId);
            Assertions.assertThat(currentDbRows).isEqualTo(List.of(initialRow));
        }
    }

    // --- INSERTION ---

    @Test
    void testDirtyRead_readUncommitted_insert() {
        testDirtyRead(Isol.READ_UNCOMMITTED, Op.Insert, Expected.DirtyRead);
    }

    @Test
    void testDirtyRead_readCommitted_insert() {
        testDirtyRead(Isol.READ_COMMITTED, Op.Insert, Expected.Soundness);
    }

    @Test
    void testDirtyRead_repeatableRead_insert() {
        testDirtyRead(Isol.REPEATABLE_READ, Op.Insert, Expected.Soundness);
    }

    @Test
    void testDirtyRead_serializable_insert() {
        testDirtyRead(Isol.SERIALIZABLE, Op.Insert, Expected.Soundness);
    }

    @Test
    void testDirtyRead_snapshot_insert() {
        testDirtyRead(Isol.SNAPSHOT, Op.Insert, Expected.Soundness);
    }

    // --- UPDATE ---

    @Test
    void testDirtyRead_readUncommitted_update() {
        testDirtyRead(Isol.READ_UNCOMMITTED, Op.Update, Expected.DirtyRead);
    }

    @Test
    void testDirtyRead_readCommitted_update() {
        testDirtyRead(Isol.READ_COMMITTED, Op.Update, Expected.Soundness);
    }

    @Test
    void testDirtyRead_repeatableRead_update() {
        testDirtyRead(Isol.REPEATABLE_READ, Op.Update, Expected.Soundness);
    }

    @Test
    void testDirtyRead_serializable_update() {
        testDirtyRead(Isol.SERIALIZABLE, Op.Update, Expected.Soundness);
    }

    @Test
    void testDirtyRead_snapshot_update() {
        testDirtyRead(Isol.SNAPSHOT, Op.Update, Expected.Soundness);
    }

    // --- DELETE ---

    @Test
    void testDirtyRead_readUncommitted_delete() {
        testDirtyRead(Isol.READ_UNCOMMITTED, Op.Delete, Expected.DirtyRead);
    }

    @Test
    void testDirtyRead_readCommitted_delete() {
        testDirtyRead(Isol.READ_COMMITTED, Op.Delete, Expected.Soundness);
    }

    @Test
    void testDirtyRead_repeatableRead_delete() {
        testDirtyRead(Isol.REPEATABLE_READ, Op.Delete, Expected.Soundness);
    }

    @Test
    void testDirtyRead_serializable_delete() {
        testDirtyRead(Isol.SERIALIZABLE, Op.Delete, Expected.Soundness);
    }

    @Test
    void testDirtyRead_snapshot_delete() {
        testDirtyRead(Isol.SNAPSHOT, Op.Delete, Expected.Soundness);
    }
}
