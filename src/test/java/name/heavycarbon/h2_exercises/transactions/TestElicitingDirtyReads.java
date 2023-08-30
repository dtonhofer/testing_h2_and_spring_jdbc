package name.heavycarbon.h2_exercises.transactions;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainer.Op;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.*;
import name.heavycarbon.h2_exercises.transactions.db.Db.AutoIncrementing;
import name.heavycarbon.h2_exercises.transactions.db.Db.CleanupFirst;
import name.heavycarbon.h2_exercises.transactions.dirty_read.AgentContainer_DirtyRead;
import name.heavycarbon.h2_exercises.transactions.dirty_read.Setup;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureJdbc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

// ---
// A "dirty read" happens when transaction T2 can read data written by,
// but not yet committed by, transaction T1. This unsoundness is supposed
// to go away at isolation level ANSI READ COMMITTED and stronger.
// ---

// ---
// We assume that the classes with the methods marked "Transactional" needs to be injected for a
// valid "transactional proxy" to be slapped around them.
// ---

@Slf4j
@AutoConfigureJdbc
@SpringBootTest(classes = {Db.class, SessionManip.class, TransactionalGateway.class})
public class TestElicitingDirtyReads {

    private enum Expected {Soundness, DirtyRead}

    // ---
    // Spring does its autowiring magic here.
    // ---

    @Autowired
    private Db db;

    @Autowired
    private TransactionalGateway txGw;

    // ---
    // Information on how to set up the database and what to modify
    // ---

    private static final StuffId initRowId = new StuffId(100);

    // initial row in database
    private static final Stuff initRow = new Stuff(initRowId, EnsembleId.Two, "INIT");

    // the initRow will be updated to this
    private static final Stuff updateRow = new Stuff(initRowId, EnsembleId.Two, "XXX");

    // this row will be additionally inserted
    private static final Stuff insertRow = new Stuff(200, EnsembleId.Two, "INSERT");

    // this row is initially in the database and will be deleted
    private static final Stuff deleteRow = new Stuff(300, EnsembleId.Two, "DELETE");

    // ---

    private void setupDb() {
        db.setupStuffTable(AutoIncrementing.Yes, CleanupFirst.Yes);
        db.insert(initRow);
        db.insert(deleteRow);
    }

    // ---


    private static Stream<Arguments> provideTestArgStream() {
        List<Arguments> res = new ArrayList<>();
        for (int i = 0; i < 1; i++) {
            res.add(Arguments.of(Isol.READ_UNCOMMITTED, Op.Insert, Expected.DirtyRead));
            res.add(Arguments.of(Isol.READ_COMMITTED, Op.Insert, Expected.Soundness));
            res.add(Arguments.of(Isol.REPEATABLE_READ, Op.Insert, Expected.Soundness));
            res.add(Arguments.of(Isol.SERIALIZABLE, Op.Insert, Expected.Soundness));
            res.add(Arguments.of(Isol.SNAPSHOT, Op.Insert, Expected.Soundness));

            res.add(Arguments.of(Isol.READ_UNCOMMITTED, Op.Update, Expected.DirtyRead));
            res.add(Arguments.of(Isol.READ_COMMITTED, Op.Update, Expected.Soundness));
            res.add(Arguments.of(Isol.REPEATABLE_READ, Op.Update, Expected.Soundness));
            res.add(Arguments.of(Isol.SERIALIZABLE, Op.Update, Expected.Soundness));
            res.add(Arguments.of(Isol.SNAPSHOT, Op.Update, Expected.Soundness));

            res.add(Arguments.of(Isol.READ_UNCOMMITTED, Op.Delete, Expected.DirtyRead));
            res.add(Arguments.of(Isol.READ_COMMITTED, Op.Delete, Expected.Soundness));
            res.add(Arguments.of(Isol.REPEATABLE_READ, Op.Delete, Expected.Soundness));
            res.add(Arguments.of(Isol.SERIALIZABLE, Op.Delete, Expected.Soundness));
            res.add(Arguments.of(Isol.SNAPSHOT, Op.Delete, Expected.Soundness));
        }
        return res.stream();
    }

    @ParameterizedTest
    @MethodSource("provideTestArgStream")
    void testDirtyRead(@NotNull Isol isol, @NotNull Op op, @NotNull Expected expected) {
        setupDb();
        final var ac = new AgentContainer_DirtyRead(db, isol, op, new Setup(initRow, updateRow, insertRow, deleteRow), txGw);
        {
            ac.startAll();
            ac.joinAll();
        }
        // None of the threads should have terminated badly!
        Assertions.assertThat(ac.isAnyThreadTerminatedBadly()).isFalse();
        // Result exist
        Assertions.assertThat(ac.getReaderRunnable().getResult()).isPresent();
        List<Stuff> result = ac.getReaderRunnable().getResult().orElseThrow();
        switch (expected) {
            case DirtyRead -> expectDirtyRead(op, result);
            case Soundness -> expectSoundness(op, result);
        }
        finallyExpectNoChangesBecauseModifierRolledBack();
        log.info("OK: Dirty Read, isolation level {}, operation {}, expected {}", isol, op, expected);
    }

    private void expectSoundness(@NotNull Op op, @NotNull List<Stuff> result) {
        List<Stuff> expectedStuff = switch (op) {
            // we did not see any updates, only the "initial row" (selection was by id)
            case Update -> List.of(initRow);
            // the row to delete was not missing (selection was by id)
            case Delete -> List.of(deleteRow);
            // the inserted row was not yet there (selection was by id)
            case Insert -> List.of();
            default -> throw new IllegalArgumentException("Unhandled op " + op);
        };
        Assertions.assertThat(result).isEqualTo(expectedStuff);
    }

    private void expectDirtyRead(@NotNull Op op, @NotNull List<Stuff> result) {
        List<Stuff> expectedStuff = switch (op) {
            // we saw the updated but uncommitted row (selection was by id)
            case Update -> List.of(updateRow);
            // the "deleted" but uncommitted row was indeed missing (selection was by id)
            case Delete -> List.of();
            // we saw the inserted but uncommitted row (selection was by id)
            case Insert -> List.of(insertRow);
            default -> throw new IllegalArgumentException("Unhandled op " + op);
        };
        Assertions.assertThat(result).isEqualTo(expectedStuff);
    }

    private void finallyExpectNoChangesBecauseModifierRolledBack() {
        List<Stuff> actualStuff = db.readAll();
        List<Stuff> expectedStuff = List.of(initRow, deleteRow);
        Assertions.assertThat(actualStuff).isEqualTo(expectedStuff);
    }

}
