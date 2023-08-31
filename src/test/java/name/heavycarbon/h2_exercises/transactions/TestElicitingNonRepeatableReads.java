package name.heavycarbon.h2_exercises.transactions;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainer.Op;
import name.heavycarbon.h2_exercises.transactions.agent.PrintException;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.*;
import name.heavycarbon.h2_exercises.transactions.non_repeatable_read.AgentContainer_NonRepeatableRead;
import name.heavycarbon.h2_exercises.transactions.non_repeatable_read.Setup;
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
// A "non-repeatable read" happens when transaction T1 reads data item D, another transaction T2
// changes that data item AND COMMITS, and then transaction T1 re-reads the data item and finds
// it has changed. This unsoundness is supposed to not occur at transaction level
// `REPEATABLE READ` and stronger.
// ---

// Assumption that the classes with the methods marked "Transactional" needs to be injected for a
// valid "transactional proxy" to be slapped around them.

@Slf4j
@AutoConfigureJdbc
@SpringBootTest(classes = {Db.class, SessionManip.class, TransactionalGateway.class})
public class TestElicitingNonRepeatableReads {

    private enum Expected {Soundness, NonRepeatableRead}

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
    private static final Stuff initRow = new Stuff(initRowId, EnsembleId.Two, "INITIAL");

    // the initRow will be updated to this
    private static final Stuff updateRow = new Stuff(initRowId, EnsembleId.Two, "UPDATED");

    // this row will be additionally inserted
    private static final Stuff insertRow = new Stuff(new StuffId(200), EnsembleId.Two, "INSERTED");

    // this row is initially in the database and will be deleted
    private static final Stuff deleteRow = new Stuff(new StuffId(300), EnsembleId.Two, "DELETED");

    // ---

    private void setupDb() {
        db.setupStuffTable(Db.AutoIncrementing.Yes, Db.CleanupFirst.Yes);
        db.insert(initRow);
        db.insert(deleteRow);
    }

    // ---

    private static Stream<Arguments> provideTestArgStream() {
        List<Arguments> res = new ArrayList<>();
        final int rounds = 1; // increase for heavier testing
        for (int i = 0; i < rounds; i++) {
            // "Unexpected changes to record" problem goes away at isolation level ANSI "REPEATABLE READ"
            res.add(Arguments.of(Isol.READ_UNCOMMITTED, Op.Update, Expected.NonRepeatableRead));
            res.add(Arguments.of(Isol.READ_COMMITTED, Op.Update, Expected.NonRepeatableRead));
            res.add(Arguments.of(Isol.REPEATABLE_READ, Op.Update, Expected.Soundness));
            res.add(Arguments.of(Isol.SERIALIZABLE, Op.Update, Expected.Soundness));
            res.add(Arguments.of(Isol.SNAPSHOT, Op.Update, Expected.Soundness));
            // "Unexpected appearance of a missing record" goes away at isolation level ANSI "REPEATABLE READ"
            // In a sense this is a "phantom read" with the predicate "selection by id" but the problem
            // actually goes away in isolation level "ANSI REPEATABLE READ".
            res.add(Arguments.of(Isol.READ_UNCOMMITTED, Op.Insert, Expected.NonRepeatableRead));
            res.add(Arguments.of(Isol.READ_COMMITTED, Op.Insert, Expected.NonRepeatableRead));
            res.add(Arguments.of(Isol.REPEATABLE_READ, Op.Insert, Expected.Soundness));
            res.add(Arguments.of(Isol.SERIALIZABLE, Op.Insert, Expected.Soundness));
            res.add(Arguments.of(Isol.SNAPSHOT, Op.Insert, Expected.Soundness));
            // "Unexpected disappearance of a record" goes away at isolation level ANSI "REPEATABLE READ"
            // In a sense this is a "phantom read" with the predicate "selection by id" but the problem
            // actually goes away in isolation level "ANSI REPEATABLE READ".
            res.add(Arguments.of(Isol.READ_UNCOMMITTED, Op.Delete, Expected.NonRepeatableRead));
            res.add(Arguments.of(Isol.READ_COMMITTED, Op.Delete, Expected.NonRepeatableRead));
            res.add(Arguments.of(Isol.REPEATABLE_READ, Op.Delete, Expected.Soundness));
            res.add(Arguments.of(Isol.SERIALIZABLE, Op.Delete, Expected.Soundness));
            res.add(Arguments.of(Isol.SNAPSHOT, Op.Delete, Expected.Soundness));
        }
        return res.stream();
    }

    @ParameterizedTest
    @MethodSource("provideTestArgStream")
    void testNonRepeatableRead(@NotNull Isol isol, @NotNull Op op, @NotNull Expected expected) {
        setupDb();
        log.info("STARTING: Non-Repeatable Read, isolation level {}, operation {}", isol, op);
        final PrintException pex = PrintException.No;
        final var ac = new AgentContainer_NonRepeatableRead(db, isol, op, new Setup(initRow, updateRow, insertRow, deleteRow), pex, txGw);
        {
            ac.startAll();
            ac.joinAll();
        }
        // None of the threads should have terminated badly!
        Assertions.assertThat(ac.isAnyThreadTerminatedBadly()).isFalse();
        // Result exist and can be extracted
        Assertions.assertThat(ac.getReaderRunnable().getResult()).isPresent();
        List<Stuff> result1 = ac.getReaderRunnable().getResult().orElseThrow().getResult1();
        List<Stuff> result2 = ac.getReaderRunnable().getResult().orElseThrow().getResult2();
        switch (expected) {
            case NonRepeatableRead -> expectNonRepeatableRead(op, result1, result2);
            case Soundness -> expectSoundness(op, result1, result2);
        }
        // Final database contents as expected?
        finallyExpectWhatTheModifierDid(op);
        log.info("OK: Non-Repeatable Read, isolation level {}, operation {}, expected {}", isol, op, expected);
    }

    private void expectNonRepeatableRead(@NotNull Op op, @NotNull List<Stuff> result1, @NotNull List<Stuff> result2) {
        List<Stuff> expectedStuff1;
        List<Stuff> expectedStuff2;
        switch (op) {
            case Update -> {
                // "initRow" was updated to "updateRow" and this was visible (selection by id)
                expectedStuff1 = List.of(initRow);
                expectedStuff2 = List.of(updateRow);
            }
            case Insert -> {
                // "insertRow" was inserted and this was visible (selection by id)
                expectedStuff1 = List.of();
                expectedStuff2 = List.of(insertRow);
            }
            case Delete -> {
                // "deleteRow" was deleted and this was visible
                expectedStuff1 = List.of(deleteRow);
                expectedStuff2 = List.of();
            }
            default -> throw new IllegalArgumentException("Unhandled op " + op);
        }
        Assertions.assertThat(Stuff.sortById(result1)).isEqualTo(Stuff.sortById(expectedStuff1));
        Assertions.assertThat(Stuff.sortById(result2)).isEqualTo(Stuff.sortById(expectedStuff2));
    }

    private void expectSoundness(@NotNull Op op, @NotNull List<Stuff> result1, @NotNull List<Stuff> result2) {
        // we just see what's initially there and nothing changes during the transaction
        List<Stuff> expectedStuff = switch (op) {
            // "initRow" was updated but none of that was visible (selection by id)
            case Update -> List.of(initRow);
            // "insertRow" was inserted but none of that was visible (selection by id)
            case Insert -> List.of();
            // "deleteRow" was deleted but none of that was visible (selection by id)
            case Delete -> List.of(deleteRow);
            default -> throw new IllegalArgumentException("Unhandled op " + op);
        };
        Assertions.assertThat(Stuff.sortById(result1)).isEqualTo(Stuff.sortById(expectedStuff));
        Assertions.assertThat(Stuff.sortById(result2)).isEqualTo(Stuff.sortById(expectedStuff));
    }

    private void finallyExpectWhatTheModifierDid(@NotNull Op op) {
        List<Stuff> actualStuff = db.readAll();
        List<Stuff> expectedStuff = switch (op) {
            // "initRow" was updated to "updateRow"
            case Update -> expectedStuff = List.of(updateRow, deleteRow);
            // "insertRow" was inserted
            case Insert -> expectedStuff = List.of(initRow, deleteRow, insertRow);
            // "deleteRow" was deleted
            case Delete -> expectedStuff = List.of(initRow);
            default -> throw new IllegalArgumentException("Unhandled op " + op);
        };
        Assertions.assertThat(Stuff.sortById(actualStuff)).isEqualTo(Stuff.sortById(expectedStuff));
    }

}