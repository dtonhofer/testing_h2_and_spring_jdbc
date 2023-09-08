package name.heavycarbon.h2_exercises.transactions;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.PrintException;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import name.heavycarbon.h2_exercises.transactions.db.SessionManip;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import name.heavycarbon.h2_exercises.transactions.dirty_read.AgentContainer;
import name.heavycarbon.h2_exercises.transactions.dirty_read.Config;
import name.heavycarbon.h2_exercises.transactions.dirty_read.Config.Op;
import name.heavycarbon.h2_exercises.transactions.dirty_read.DbConfig;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureJdbc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Collections;
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

    private DbConfig dbConfig = new DbConfig();

    // ---

    private void setupDb() {
        db.rejuvenateSchema();
        db.createStuffTable();
        db.insert(dbConfig.initRow);
        db.insert(dbConfig.deleteRow);
    }

    // ---
    // JUnit gets the arguments for the test method from this
    // ---

    private static Stream<Arguments> provideTestArgStream() {
        List<Arguments> res = new ArrayList<>();
        for (int i = 0; i < rounds; i++) {
            for (Isol isol : Isol.values()) {
                for (Op op : List.of(Op.Insert, Op.Update, Op.Delete)) {
                    // We expect "Soundness" of the operation (i.e. NO "dirty read" aka
                    // "reading of data yet to be committed") in all cases EXCEPT in
                    // isolation level "ANSI READ (even) UNCOMMITTED".
                    final var expected = (isol == Isol.READ_UNCOMMITTED) ? Expected.DirtyRead : Expected.Soundness;
                    res.add(Arguments.of(new Config(isol, op, PrintException.No), expected));
                }
            }
        }
        return res.stream();
    }

    // ---
    // Increase this for running the test more than once in a loop
    // ---

    private final static int rounds = 1;

    // ---
    // The test method to call. As the annotation says, it gets its arguments from
    // method "provideTestArgStream".
    // ---

    @ParameterizedTest
    @MethodSource("provideTestArgStream")
    void testDirtyRead(@NotNull Config config, @NotNull Expected expected) {
        setupDb();
        final var ac = new AgentContainer(db, txGw, config, dbConfig);
        {
            ac.startAll();
            ac.joinAll();
        }
        // None of the threads should have terminated badly!
        Assertions.assertThat(ac.isAnyAgentTerminatedBadly()).isFalse();
        // Result exist
        Assertions.assertThat(ac.getReaderRunnable().getResult()).isPresent();
        List<Stuff> result = Collections.unmodifiableList(ac.getReaderRunnable().getResult().orElseThrow());
        switch (expected) {
            case DirtyRead -> assertDirtyRead(config.op(), result);
            case Soundness -> assertSoundness(config.op(), result);
        }
        finallyExpectNoChangesBecauseModifierRolledBack();
        log.info("OK: Dirty Read, isolation level {}, operation {}, expected {}", config.isol(), config.op(), expected);
    }

    private void assertSoundness(@NotNull Op op, @NotNull List<Stuff> result) {
        List<Stuff> expectedStuff = switch (op) {
            // we did not see any updates, only the "initial row" (selection was by id)
            case Update -> List.of(dbConfig.initRow);
            // the row to delete was not missing (selection was by id)
            case Delete -> List.of(dbConfig.deleteRow);
            // the inserted row was not yet there (selection was by id)
            case Insert -> List.of();
            default -> throw new IllegalArgumentException("Unhandled op " + op);
        };
        Assertions.assertThat(result).isEqualTo(expectedStuff);
    }

    private void assertDirtyRead(@NotNull Op op, @NotNull List<Stuff> result) {
        List<Stuff> expectedStuff = switch (op) {
            // we saw the updated but uncommitted row (selection was by id)
            case Update -> List.of(dbConfig.updateRow);
            // the "deleted" but uncommitted row was indeed missing (selection was by id)
            case Delete -> List.of();
            // we saw the inserted but uncommitted row (selection was by id)
            case Insert -> List.of(dbConfig.insertRow);
            default -> throw new IllegalArgumentException("Unhandled op " + op);
        };
        Assertions.assertThat(result).isEqualTo(expectedStuff);
    }

    private void finallyExpectNoChangesBecauseModifierRolledBack() {
        List<Stuff> actualStuff = db.readAll();
        List<Stuff> expectedStuff = List.of(dbConfig.initRow, dbConfig.deleteRow);
        Assertions.assertThat(actualStuff).isEqualTo(expectedStuff);
    }

}
