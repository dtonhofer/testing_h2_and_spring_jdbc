package name.heavycarbon.h2_exercises.transactions;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.PrintException;
import name.heavycarbon.h2_exercises.transactions.common.DualListOfStuff;
import name.heavycarbon.h2_exercises.transactions.common.ListOfStuffHandling.WhatDo;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import name.heavycarbon.h2_exercises.transactions.db.SessionManip;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import name.heavycarbon.h2_exercises.transactions.phantom_read.AgentContainer_PhantomRead;
import name.heavycarbon.h2_exercises.transactions.phantom_read.Config;
import name.heavycarbon.h2_exercises.transactions.phantom_read.Config.Op;
import name.heavycarbon.h2_exercises.transactions.phantom_read.Config.PhantomicPredicate;
import name.heavycarbon.h2_exercises.transactions.phantom_read.DbConfig;
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
// "Phantom Reads" are a "strong" unsoundness that occur in isolation level READ_UNCOMMITTED and lower.
// You need to switch to SERIALIZABLE or SNAPSHOT to get rid of it.
// ---

// Assumption that the classes with the methods marked "Transactional" needs to be injected for a
// valid "transactional proxy" to be slapped around Agent1Transactional.

@Slf4j
@AutoConfigureJdbc
@SpringBootTest(classes = {Db.class, SessionManip.class, TransactionalGateway.class})
public class TestElicitingPhantomReads {

    private enum Expected {Soundness, PhantomRead}

    // ---
    // Spring does its autowiring magic here.
    // ---

    @Autowired
    private Db db;

    @Autowired
    private TransactionalGateway txGw;

    // ---
    // Information about records that can be found in the test db
    // ---

    private final DbConfig dbConfig = new DbConfig();

    private void setupDb() {
        db.rejuvenateSchema();
        db.createStuffTable();
        dbConfig.initialStuff.forEach(db::insert);
    }

    // ---
    // JUnit gets the arguments for the test method from this
    // ---

    private static Stream<Arguments> provideTestArgStream() {
        List<Arguments> res = new ArrayList<>();
        for (int i = 0; i < rounds; i++) {
            for (Isol isol : Isol.values()) {
                for (Op op : Op.values()) {
                    for (PhantomicPredicate pred : PhantomicPredicate.values()) {
                        // ...normally, one would expect unsoundness at these levels
                        // ANSI READ (even) UNCOMMITTED, ANSI READ (only) COMMITTED, ANSI REPEATABLE READ
                        // ... but in H2, Phantom Reads are already fixed at isolation level ANSI READ COMMITTED
                        // - Either because my predicates are bad
                        // - or because H2 is conservative (i.e. the implementation just fixes the problem
                        //   at lower levels; and why not)
                        final var expected = switch (isol) {
                            case READ_UNCOMMITTED -> Expected.PhantomRead;
                            case READ_COMMITTED -> Expected.PhantomRead;
                            case REPEATABLE_READ -> Expected.Soundness; // UNEXPECTED!!
                            default -> Expected.Soundness;
                        };
                        res.add(Arguments.of(new Config(isol, op, pred, PrintException.No), expected));
                    }
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

    // TODO: Handle the cases which are "unexpectedly sound" separately, as for the "Non Repeatable Reads"

    @ParameterizedTest
    @MethodSource("provideTestArgStream")
    void testPhantomRead(@NotNull Config config, @NotNull Expected expected) {
        setupDb();
        // Initial setup as expected?
        Assertions.assertThat(db.readAll()).isEqualTo(Stuff.sortById(dbConfig.initialStuff));
        // Let's go!
        log.info("STARTING: Phantom Read, isolation level {}, operation {}, predicate {}, expecting {}", config.isol(), config.op(), config.phantomicPredicate(), expected);
        final var ac = new AgentContainer_PhantomRead(db, txGw, config, dbConfig);
        {
            ac.startAll();
            ac.joinAll();
        }
        // None of the threads should have terminated badly!
        Assertions.assertThat(ac.isAnyAgentTerminatedBadly()).isFalse();
        // Results exist and can be extracted!
        Assertions.assertThat(ac.getReaderRunnable().getResult()).isPresent();
        // So let's extract!
        final DualListOfStuff dual = ac.getReaderRunnable().getResult().orElseThrow();
        final List<Stuff> result1 = Collections.unmodifiableList(dual.getResult1());
        final List<Stuff> result2 = Collections.unmodifiableList(dual.getResult2());
        switch (expected) {
            case PhantomRead -> assertPhantomRead(config, result1, result2);
            case Soundness -> assertSoundness(config, result1, result2);
        }
        // Final database contents as expected?
        finallyExpectWhatTheModifierDid(config.op());
        log.info("OK: Phantom Read, isolation level {}, operation {}, predicate {}, expected {}", config.isol(), config.op(), config.phantomicPredicate(), expected);
    }

    // ---
    // In 0.6% of the cases, we get unexpected "soundness" in READ_COMMITTED!
    // H2 is unexpectedly stricter than necessary. We move such cases to a special assertion failure.
    // ---

    private void assertPhantomRead(@NotNull Config config, @NotNull List<Stuff> result1, @NotNull List<Stuff> result2) {
        if (config.isol() == Isol.READ_COMMITTED && checkSoundness(config, result1, result2, WhatDo.Check)) {
            Assertions.fail("Unexpectedly stronger isolation level than '" + config.isol() + "' for '" + config.op() + "'");
        } else {
            assertPhantomRead_firstPart(config, result1);
            assertPhantomRead_secondPart(config, result2);
        }
    }

    private void assertSoundness(@NotNull Config config, @NotNull List<Stuff> result1, @NotNull List<Stuff> result2) {
        checkSoundness(config, result1, result2, WhatDo.Assert);
    }

    private void assertPhantomRead_firstPart(@NotNull Config config, @NotNull List<Stuff> actual) {
        final List<Stuff> expected = switch (config.phantomicPredicate()) {
            case ByEnsemble -> dbConfig.getInitialRecordsWithDesiredEnsemble();
            case BySuffix -> dbConfig.getInitialRecordsWithDesiredSuffix();
            case ByEnsembleAndSuffix -> dbConfig.getInitialRecordsWithDesiredEnsembleAndSuffix();
        };
        assertListEquality(actual, expected);
    }

    // ---

    private void assertPhantomRead_secondPart(@NotNull Config config, @NotNull List<Stuff> actual) {
        switch (config.op()) {
            case Insert -> assertPhantomRead_secondPart_insert(config, actual); //
            case Delete -> assertPhantomRead_secondPart_delete(config, actual); //
            case UpdateInto -> assertPhantomRead_secondPart_updatedInto(config, actual); //
            case UpdateOutOf -> assertPhantomRead_secondReadPart_updateOutOf(config, actual); //
            default -> throw new IllegalArgumentException("Unhandled op " + config.op());
        }
    }

    // ---
    // A record is inserted and appears in the second read!
    // ---

    private void assertPhantomRead_secondPart_insert(@NotNull Config config, @NotNull List<Stuff> actual) {
        final PhantomicPredicate pp = config.phantomicPredicate();
        final Stuff ins = dbConfig.insertMe;
        final List<Stuff> expected = switch (pp) {
            case ByEnsemble -> dbConfig.getInitialRecordsWithDesiredEnsembleAndAdd(ins);
            case BySuffix -> dbConfig.getInitialRecordsWithDesiredSuffixAndAdd(ins);
            case ByEnsembleAndSuffix -> dbConfig.getInitialRecordsWithDesiredEnsembleAndSuffixAndAdd(ins);
        };
        assertListEquality(actual, expected);
    }

    // ---
    // A record is deleted and disappears in the second read!
    // ---

    private void assertPhantomRead_secondPart_delete(@NotNull Config config, @NotNull List<Stuff> actual) {
        final PhantomicPredicate pp = config.phantomicPredicate();
        final Stuff del = dbConfig.deleteMe;
        final List<Stuff> expected = switch (pp) {
            case ByEnsemble -> dbConfig.getInitialRecordsWithDesiredEnsembleAndRemove(del);
            case BySuffix -> dbConfig.getInitialRecordsWithDesiredSuffixAndRemove(del);
            case ByEnsembleAndSuffix -> dbConfig.getInitialRecordsWithDesiredEnsembleAndSuffixAndRemove(del);
        };
        assertListEquality(actual, expected);
    }

    // ---
    // A record is updated & moved into the set, it becomes visible immediately!
    // ---

    private void assertPhantomRead_secondPart_updatedInto(@NotNull Config config, @NotNull List<Stuff> actual) {
        final PhantomicPredicate pp = config.phantomicPredicate();
        final Stuff updated = dbConfig.updateForMovingInChanged;
        final List<Stuff> expected = switch (pp) {
            case ByEnsemble -> dbConfig.getInitialRecordsWithDesiredEnsembleAndAdd(updated);
            case BySuffix -> dbConfig.getInitialRecordsWithDesiredSuffixAndAdd(updated);
            case ByEnsembleAndSuffix -> dbConfig.getInitialRecordsWithDesiredEnsembleAndSuffixAndAdd(updated);
        };
        assertListEquality(actual, expected);
    }

    // ---
    // A record is updated & moved out of the set, it becomes invisible immediately!
    // ---

    private void assertPhantomRead_secondReadPart_updateOutOf(@NotNull Config config, @NotNull List<Stuff> actual) {
        final PhantomicPredicate pp = config.phantomicPredicate();
        final Stuff update = dbConfig.updateForMovingOut;
        final List<Stuff> expected = switch (pp) {
            case ByEnsemble -> dbConfig.getInitialRecordsWithDesiredEnsembleAndRemove(update);
            case BySuffix -> dbConfig.getInitialRecordsWithDesiredSuffixAndRemove(update);
            case ByEnsembleAndSuffix -> dbConfig.getInitialRecordsWithDesiredEnsembleAndSuffixAndRemove(update);
        };
        assertListEquality(actual, expected);
    }

    // ---
    // "Soundness" means we just see what's initially there and nothing changes during the transaction!
    // But what is initially there depends on the predicate of course.
    // ---

    private boolean checkSoundness(@NotNull Config config, @NotNull List<Stuff> result1, @NotNull List<Stuff> result2, @NotNull WhatDo whatDo) {
        final PhantomicPredicate pp = config.phantomicPredicate();
        final List<Stuff> expected = switch (pp) {
            // reading by selecting on column "ensemble" (selecting those belonging to ensemble one)
            case ByEnsemble -> dbConfig.getInitialRecordsWithDesiredEnsemble();
            // reading by selecting on column "payload" (selecting those ending in "AA")
            case BySuffix -> dbConfig.getInitialRecordsWithDesiredSuffix();
            // reading by selecting on both columns "ensemble" and "payload"
            case ByEnsembleAndSuffix -> dbConfig.getInitialRecordsWithDesiredEnsembleAndSuffix();
        };
        // the initial result set does NOT CHANGE upon second read!
        if (whatDo == WhatDo.Check) {
            return checkListEquality(result1, expected) && checkListEquality(result2, expected);
        } else {
            assertListEquality(result1, expected);
            assertListEquality(result2, expected);
            return true;
        }
    }

    // ---

    private void finallyExpectWhatTheModifierDid(@NotNull Op op) {
        final List<Stuff> actual = db.readAll();
        final List<Stuff> expected = computeWhatTheModifierDid(op);
        assertListEquality(actual, expected);
    }

    // ---

    private List<Stuff> computeWhatTheModifierDid(@NotNull Op op) {
        return switch (op) {
            // "setup.updateForMovingIn" was updated
            case UpdateInto ->
                    dbConfig.getInitialRecordsAndReplace(dbConfig.updateForMovingIn, dbConfig.updateForMovingInChanged);
            // "setup.updateForMovingOut" was updated
            case UpdateOutOf ->
                    dbConfig.getInitialRecordsAndReplace(dbConfig.updateForMovingOut, dbConfig.updateForMovingOutChanged);
            // "dbConfig.insertMe" was inserted
            case Insert -> dbConfig.getInitialRecordsAndAdd(dbConfig.insertMe);
            // "dbConfig.deleteMe" was deleted
            case Delete -> dbConfig.getInitialRecordsAndRemove(dbConfig.deleteMe);
        };
    }

}