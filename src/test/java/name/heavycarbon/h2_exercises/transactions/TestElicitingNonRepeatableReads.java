package name.heavycarbon.h2_exercises.transactions;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.PrintException;
import name.heavycarbon.h2_exercises.transactions.common.ListOfStuffHandling.WhatDo;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import name.heavycarbon.h2_exercises.transactions.db.SessionManip;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import name.heavycarbon.h2_exercises.transactions.non_repeatable_read.AgentContainer_NonRepeatableRead;
import name.heavycarbon.h2_exercises.transactions.non_repeatable_read.Config;
import name.heavycarbon.h2_exercises.transactions.non_repeatable_read.Config.Op;
import name.heavycarbon.h2_exercises.transactions.non_repeatable_read.DbConfig;
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

import static name.heavycarbon.h2_exercises.transactions.common.ListOfStuffHandling.assertListEquality;
import static name.heavycarbon.h2_exercises.transactions.common.ListOfStuffHandling.checkListEquality;

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

    private final DbConfig dbConfig = new DbConfig();

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
                    // We expect "Soundness" of the operation (i.e. NO "non-repeatable read" aka
                    // "re-reading of data changed & committed by another transaction becomes
                    // visible in current transaction") in all cases except in isolation levels
                    // "ANSI READ (even) UNCOMMITTED" and "ANSI READ (only) COMMITTED"
                    // This covers:
                    // "Unexpected changes to record"
                    // "Unexpected appearance of a missing record" (In a sense this is a "phantom read" with the predicate "selection by id")
                    // "Unexpected disappearance of a record" (In a sense this is a "phantom read" with the predicate "selection by id")
                    final var expected = switch (isol) {
                        case READ_UNCOMMITTED -> Expected.NonRepeatableRead;
                        case READ_COMMITTED -> Expected.NonRepeatableRead;
                        default -> Expected.Soundness;
                    };
                    res.add(Arguments.of(new Config(isol, op, PrintException.No), expected));
                }
            }
        }
        return res.stream();
    }

    // ---
    // Increase this for running the test more than once in a loop
    // ---

    private final static int rounds = 100;

    // ---
    // The test method to call. As the annotation says, it gets its arguments from
    // method "provideTestArgStream".
    // ---

    @ParameterizedTest
    @MethodSource("provideTestArgStream")
    void testNonRepeatableRead(@NotNull Config config, @NotNull Expected expected) {
        setupDb();
        log.info("STARTING: Non-Repeatable Read, isolation level {}, operation {}", config.isol(), config.op());
        final var ac = new AgentContainer_NonRepeatableRead(db, txGw, config, dbConfig);
        {
            ac.startAll();
            ac.joinAll();
        }
        // None of the threads should have terminated badly!
        Assertions.assertThat(ac.isAnyAgentTerminatedBadly()).isFalse();
        // Result exist and can be extracted
        Assertions.assertThat(ac.getReaderRunnable().getResult()).isPresent();
        // So extract!
        final List<Stuff> result1 = Collections.unmodifiableList(ac.getReaderRunnable().getResult().orElseThrow().getResult1());
        final List<Stuff> result2 = Collections.unmodifiableList(ac.getReaderRunnable().getResult().orElseThrow().getResult2());
        switch (expected) {
            case NonRepeatableRead -> assertNonRepeatableRead(config, result1, result2);
            case Soundness -> assertSoundness(config, result1, result2);
        }
        // Final database contents as expected?
        finallyExpectWhatTheModifierDid(config.op());
        log.info("OK: Non-Repeatable Read, isolation level {}, operation {}, expected {}", config.isol(), config.op(), expected);
    }

    private void assertSoundness(@NotNull Config config, @NotNull List<Stuff> result1, @NotNull List<Stuff> result2) {
        checkSoundness(config.op(), result1, result2, WhatDo.Assert);
    }

    private void assertNonRepeatableRead(@NotNull Config config, @NotNull List<Stuff> result1, @NotNull List<Stuff> result2) {
        List<Stuff> expected1;
        List<Stuff> expected2;
        switch (config.op()) {
            case Update -> {
                // "initRow" was updated to "updateRow" and this was visible (selection by id)
                expected1 = List.of(dbConfig.initRow);
                expected2 = List.of(dbConfig.updateRow);
            }
            case Insert -> {
                // "insertRow" was inserted and this was visible (selection by id)
                expected1 = List.of();
                expected2 = List.of(dbConfig.insertRow);
            }
            case Delete -> {
                // "deleteRow" was deleted and this was visible
                expected1 = List.of(dbConfig.deleteRow);
                expected2 = List.of();
            }
            default -> throw new IllegalArgumentException("Unhandled op " + config.op());
        }
        //
        // Sometimes H2 unexpectedly (0.6%) isolates the transaction as if we were are higher isolation levels!
        // Generate a specific assertion error for that case!
        //
        final boolean specialCheck = true;
        if (specialCheck) {
            if (config.isol() == Isol.READ_COMMITTED && checkSoundness(config.op(), result1, result2, WhatDo.Check)) {
                Assertions.fail("Unexpectedly stronger isolation level than '" + config.isol() + "' for '" + config.op() + "'");
            }
        }
        assertListEquality(result1, expected1);
        assertListEquality(result2, expected2);
    }

    private boolean checkSoundness(@NotNull Op op, @NotNull List<Stuff> result1, @NotNull List<Stuff> result2, @NotNull WhatDo whatDo) {
        // we just see what's initially there and nothing changes during the transaction
        List<Stuff> expected = switch (op) {
            // "initRow" was updated but none of that was visible (selection by id)
            case Update -> List.of(dbConfig.initRow);
            // "insertRow" was inserted but none of that was visible (selection by id)
            case Insert -> List.of();
            // "deleteRow" was deleted but none of that was visible (selection by id)
            case Delete -> List.of(dbConfig.deleteRow);
            default -> throw new IllegalArgumentException("Unhandled op " + op);
        };
        if (whatDo == WhatDo.Check) {
            return checkListEquality(result1, expected) && checkListEquality(result2, expected);
        } else {
            assertListEquality(result1, expected);
            assertListEquality(result2, expected);
            return true;
        }
    }

    private void finallyExpectWhatTheModifierDid(@NotNull Op op) {
        List<Stuff> actualStuff = db.readAll();
        List<Stuff> expectedStuff = switch (op) {
            // "initRow" was updated to "updateRow"
            case Update -> List.of(dbConfig.updateRow, dbConfig.deleteRow);
            // "insertRow" was inserted
            case Insert -> List.of(dbConfig.initRow, dbConfig.deleteRow, dbConfig.insertRow);
            // "deleteRow" was deleted
            case Delete -> List.of(dbConfig.initRow);
        };
        assertListEquality(actualStuff, expectedStuff);
    }

}