package name.heavycarbon.h2_exercises.transactions;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainerBase.Op;
import name.heavycarbon.h2_exercises.transactions.agent.TransactionResult2;
import name.heavycarbon.h2_exercises.transactions.common.WhatToReadByEnsemble;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.EnsembleId;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import name.heavycarbon.h2_exercises.transactions.non_repeatable_read.AgentContainer_NonRepeatableRead;
import name.heavycarbon.h2_exercises.transactions.non_repeatable_read.Transactional_NonRepeatableRead_Modifier;
import name.heavycarbon.h2_exercises.transactions.non_repeatable_read.Transactional_NonRepeatableRead_Reader;
import name.heavycarbon.h2_exercises.transactions.session.Isol;
import name.heavycarbon.h2_exercises.transactions.session.SessionManip;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureJdbc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

// ---
// "Non-Repeatable Reads" are a moderate unsoundness that occur in isolation level READ_COMMITTED and lower.
// ---

// Assumption that the classes with the methods marked "Transactional" needs to be injected for a
// valid "transactional proxy" to be slapped around them.

@Slf4j
@AutoConfigureJdbc
@SpringBootTest(classes = {Db.class, SessionManip.class, Transactional_NonRepeatableRead_Modifier.class, Transactional_NonRepeatableRead_Reader.class})
public class TestElicitingNonRepeatableReads {

    private enum Expected {Sound, NonRepeatableRead}

    @Autowired
    private Db db;

    @Autowired
    private Transactional_NonRepeatableRead_Modifier inserterTx;

    @Autowired
    private Transactional_NonRepeatableRead_Reader readerTx;

    private record SetupData(@NotNull List<Stuff> initialRows, @NotNull StuffId modifyThisStuffId) {
    }

    // the value of the second column, arbitrary

    private static final EnsembleId theEnsembleId = EnsembleId.Two;

    private @NotNull Stuff setupDbReturnInitialRow() {
        db.setupDatabase(true);
        db.insert(theEnsembleId, "AAA");
        final List<Stuff> initialRows = db.readEnsemble(theEnsembleId);
        assert initialRows.size() == 1;
        return initialRows.get(0);
    }

    private void testNonRepeatableRead(@NotNull Isol isol, @NotNull Op op, @NotNull Expected expected) {
        final Stuff initialRow = setupDbReturnInitialRow();
        final var ac = new AgentContainer_NonRepeatableRead(db, inserterTx, readerTx, isol, op);
        ac.setWhatToModify(initialRow.id());
        ac.setWhatToRead(new WhatToReadByEnsemble(theEnsembleId));
        // Let's run it
        ac.startAll();
        ac.joinAll();
        // None of the threads should have terminated badly!
        Assertions.assertThat(ac.isAnyThreadTerminatedBadly()).isFalse();
        {
            final Optional<TransactionResult2> optResult = ac.getReaderRunnable().getResult();
            Assertions.assertThat(optResult).isPresent();
            {
                final List<Stuff> readResult1 = optResult.get().readResult1();
                Assertions.assertThat(readResult1).isEqualTo(List.of(initialRow));
            }
            {
                final List<Stuff> readResult2 = optResult.get().readResult2();
                switch (expected) {
                    case NonRepeatableRead -> {
                        readResultMustBeWhatTheModifierDid(op, initialRow, readResult2);
                    }
                    case Sound -> {
                        Assertions.assertThat(readResult2).isEqualTo(List.of(initialRow));
                    }
                }
            }
        }
        readResultMustBeWhatTheModifierDid(op, initialRow, db.readEnsemble(EnsembleId.Two));
    }

    private void readResultMustBeWhatTheModifierDid(@NotNull Op op, @NotNull Stuff initialRow, @NotNull List<Stuff> readResult) {
        switch (op) {
            case Insert -> {
                Assertions.assertThat(readResult).hasSize(2);
                Assertions.assertThat(readResult).contains(initialRow);
            }
            case Update -> {
                Assertions.assertThat(readResult).hasSize(1);
                final Stuff readRowActual = readResult.get(0);
                final Stuff readRowExpected = initialRow.with("XXX");
                Assertions.assertThat(readRowActual).isEqualTo(readRowExpected);
            }
            case Delete -> {
                Assertions.assertThat(readResult).isEmpty();
            }
        }
    }

    // --- INSERTION ---

    @Test
    void testNonRepeatableRead_readUncommitted_insert() {
        testNonRepeatableRead(Isol.READ_UNCOMMITTED, Op.Insert, Expected.NonRepeatableRead);
    }

    @Test
    void testNonRepeatableRead_readCommitted_insert() {
        testNonRepeatableRead(Isol.READ_COMMITTED, Op.Insert, Expected.NonRepeatableRead);
    }

    @Test
    void testNonRepeatableRead_repeatableRead_insert() {
        testNonRepeatableRead(Isol.REPEATABLE_READ, Op.Insert, Expected.Sound);
    }

    @Test
    void testNonRepeatableRead_serializable_insert() {
        testNonRepeatableRead(Isol.SERIALIZABLE, Op.Insert, Expected.Sound);
    }

    @Test
    void testNonRepeatableRead_snapshot_insert() {
        testNonRepeatableRead(Isol.SNAPSHOT, Op.Insert, Expected.Sound);
    }

    // --- UPDATE ---

    @Test
    void testNonRepeatableRead_readUncommitted_update() {
        testNonRepeatableRead(Isol.READ_UNCOMMITTED, Op.Update, Expected.NonRepeatableRead);
    }

    @Test
    void testNonRepeatableRead_readCommitted_update() {
        testNonRepeatableRead(Isol.READ_COMMITTED, Op.Update, Expected.NonRepeatableRead);
    }

    @Test
    void testNonRepeatableRead_repeatableRead_update() {
        testNonRepeatableRead(Isol.REPEATABLE_READ, Op.Update, Expected.Sound);
    }

    @Test
    void testNonRepeatableRead_serializable_update() {
        testNonRepeatableRead(Isol.SERIALIZABLE, Op.Update, Expected.Sound);
    }

    @Test
    void testNonRepeatableRead_snapshot_update() {
        testNonRepeatableRead(Isol.SNAPSHOT, Op.Update, Expected.Sound);
    }

    // --- DELETE ---

    @Test
    void testNonRepeatableRead_readUncommitted_delete() {
        testNonRepeatableRead(Isol.READ_UNCOMMITTED, Op.Delete, Expected.NonRepeatableRead);
    }

    @Test
    void testNonRepeatableRead_readCommitted_delete() {
        testNonRepeatableRead(Isol.READ_COMMITTED, Op.Delete, Expected.NonRepeatableRead);
    }

    @Test
    void testNonRepeatableRead_repeatableRead_delete() {
        testNonRepeatableRead(Isol.REPEATABLE_READ, Op.Delete, Expected.Sound);
    }

    @Test
    void testNonRepeatableRead_serializable_delete() {
        testNonRepeatableRead(Isol.SERIALIZABLE, Op.Delete, Expected.Sound);
    }

    @Test
    void testNonRepeatableRead_snapshot_delete() {
        testNonRepeatableRead(Isol.SNAPSHOT, Op.Delete, Expected.Sound);
    }

}