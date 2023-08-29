package name.heavycarbon.h2_exercises.transactions;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainer.Op;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import name.heavycarbon.h2_exercises.transactions.db.SessionManip;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import name.heavycarbon.h2_exercises.transactions.phantom_read.AgentContainer_PhantomRead;
import name.heavycarbon.h2_exercises.transactions.phantom_read.AgentContainer_PhantomRead.PhantomicPredicate;
import name.heavycarbon.h2_exercises.transactions.phantom_read.Setup;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureJdbc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

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
    // Configuration about what to insert, delete, update
    // ---

    private final Setup setup = new Setup();

    // ---

    private void setupDb() {
        db.setupDatabase(Db.AutoIncrementing.Yes, Db.CleanupFirst.Yes);
        setup.initialStuff.forEach(db::insert);
    }

    // ---

    @ParameterizedTest
    @MethodSource("provideTestArgStream")
    void testDirtyRead(@NotNull Isol isol, @NotNull Op op, @NotNull PhantomicPredicate pred, @NotNull Expected expected) {
        setupDb();
        // Initial setup as expected?
        Assertions.assertThat(db.readAll()).isEqualTo(Stuff.sortById(setup.initialStuff));
        // Let's go!
        log.info("STARTING: Non-Repeatable Read, isolation level {}, operation {}, predicate {}, expecting {}", isol, op, pred, expected);
        final var ac = new AgentContainer_PhantomRead(db, isol, op, pred, setup, txGw);
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
        // Was the result sound or where phantom reads detected?
        switch (expected) {
            case PhantomRead -> expectPhantomRead(op, pred, result1, result2);
            case Soundness -> expectSoundness(pred, result1, result2);
        }
        // Final database contents as expected?
        finallyExpectWhatTheModifierDid(op);
        log.info("OK: Non-Repeatable Read, isolation level {}, operation {}, predicate {}, expected {}", isol, op, pred, expected);
    }

    private void expectPhantomRead_firstRead(@NotNull PhantomicPredicate pred, @NotNull List<Stuff> result1) {
        List<Stuff> expectedStuff1 = switch (pred) {
            case ByEnsemble -> setup.getInitialRecordsInDesiredEnsemble();
            case ByPayload -> setup.getInitialRecordsWithMatchingSuffix();
            case ByEnsembleAndPayload -> setup.getInitialRecordsInDesiredEnsembleWithMatchingSuffix();
            default -> throw new IllegalArgumentException("Unhandled predicate " + pred);
        };
        Assertions.assertThat(Stuff.sortById(result1)).isEqualTo(Stuff.sortById(expectedStuff1));
    }

    private void expectPhantomRead_secondRead(@NotNull Op op, @NotNull PhantomicPredicate pred, @NotNull List<Stuff> result2) {
        switch (op) {
            case Insert -> {
                // A record is inserted and appears in the second read!
                switch (pred) {
                    case ByEnsemble -> {
                        List<Stuff> expectedStuff2 = setup.getInitialRecordsInDesiredEnsemble_AddRecord(setup.insertMe);
                        Assertions.assertThat(Stuff.sortById(result2)).isEqualTo(Stuff.sortById(expectedStuff2));
                    }
                    case ByPayload -> {
                        List<Stuff> expectedStuff2 = setup.getInitialRecordsWithMatchingSuffix_AddRecord(setup.insertMe);
                        Assertions.assertThat(Stuff.sortById(result2)).isEqualTo(Stuff.sortById(expectedStuff2));
                    }
                    case ByEnsembleAndPayload -> {
                        List<Stuff> expectedStuff2 = setup.getInitialRecordsInDesiredEnsembleWithMatchingSuffix_AddRecord(setup.insertMe);
                        Assertions.assertThat(Stuff.sortById(result2)).isEqualTo(Stuff.sortById(expectedStuff2));
                    }
                    default -> throw new IllegalArgumentException("Unhandled pred " + pred);
                }
            }
            case Delete -> {
                // A record is deleted and disappears in the second read!
                switch (pred) {
                    case ByEnsemble -> {
                        List<Stuff> expectedStuff2 = setup.getInitialRecordsInDesiredEnsemble_RemoveRecord(setup.deleteMe);
                        Assertions.assertThat(Stuff.sortById(result2)).isEqualTo(Stuff.sortById(expectedStuff2));
                    }
                    case ByPayload -> {
                        List<Stuff> expectedStuff2 = setup.getInitialRecordsWithMatchingSuffix_RemoveRecord(setup.deleteMe);
                        Assertions.assertThat(Stuff.sortById(result2)).isEqualTo(Stuff.sortById(expectedStuff2));
                    }
                    case ByEnsembleAndPayload -> {
                        List<Stuff> expectedStuff2 = setup.getInitialRecordsInDesiredEnsembleWithMatchingSuffix_RemoveRecord(setup.deleteMe);
                        Assertions.assertThat(Stuff.sortById(result2)).isEqualTo(Stuff.sortById(expectedStuff2));
                    }
                    default -> throw new IllegalArgumentException("Unhandled pred " + pred);
                }

            }
            case UpdateIntoPredicateSet -> {
                // A record is updated & moved into the set, it becomes visible immediately!
                switch (pred) {
                    case ByEnsemble -> {
                        List<Stuff> expectedStuff2 = setup.getInitialRecordsInDesiredEnsemble_AddRecord(setup.updateForMovingInChanged);
                        Assertions.assertThat(Stuff.sortById(result2)).isEqualTo(Stuff.sortById(expectedStuff2));
                    }
                    case ByPayload -> {
                        List<Stuff> expectedStuff2 = setup.getInitialRecordsWithMatchingSuffix_AddRecord(setup.updateForMovingInChanged);
                        Assertions.assertThat(Stuff.sortById(result2)).isEqualTo(Stuff.sortById(expectedStuff2));
                    }
                    case ByEnsembleAndPayload -> {
                        List<Stuff> expectedStuff2 = setup.getInitialRecordsInDesiredEnsembleWithMatchingSuffix_AddRecord(setup.updateForMovingInChanged);
                        Assertions.assertThat(Stuff.sortById(result2)).isEqualTo(Stuff.sortById(expectedStuff2));
                    }
                    default -> throw new IllegalArgumentException("Unhandled pred " + pred);
                }
            }
            case UpdateOutOfPredicateSet -> {
                // A record is updated & moved out of the set, it becomes invisible immediately!
                switch (pred) {
                    case ByEnsemble -> {
                        List<Stuff> expectedStuff2 = setup.getInitialRecordsInDesiredEnsemble_RemoveRecord(setup.updateForMovingOut);
                        Assertions.assertThat(Stuff.sortById(result2)).isEqualTo(Stuff.sortById(expectedStuff2));
                    }
                    case ByPayload -> {
                        List<Stuff> expectedStuff2 = setup.getInitialRecordsWithMatchingSuffix_RemoveRecord(setup.updateForMovingOut);
                        Assertions.assertThat(Stuff.sortById(result2)).isEqualTo(Stuff.sortById(expectedStuff2));
                    }
                    case ByEnsembleAndPayload -> {
                        List<Stuff> expectedStuff2 = setup.getInitialRecordsInDesiredEnsembleWithMatchingSuffix_RemoveRecord(setup.updateForMovingOut);
                        Assertions.assertThat(Stuff.sortById(result2)).isEqualTo(Stuff.sortById(expectedStuff2));
                    }
                    default -> throw new IllegalArgumentException("Unhandled pred " + pred);
                }
            }
            default -> throw new IllegalArgumentException("Unhandled op " + op);
        }
    }

    private void expectPhantomRead(@NotNull Op op, @NotNull PhantomicPredicate pred, @NotNull List<Stuff> result1, @NotNull List<Stuff> result2) {
        expectPhantomRead_firstRead(pred, result1);
        expectPhantomRead_secondRead(op, pred, result2);
    }

    // ---
    // "Soundness" means we just see what's initially there and nothing changes during the transaction!
    // But what is initially there depends on the predicate.
    // ---

    private void expectSoundness(@NotNull PhantomicPredicate pred, @NotNull List<Stuff> result1, @NotNull List<Stuff> result2) {
        List<Stuff> expected = switch (pred) {
            case ByEnsemble -> setup.getInitialRecordsInDesiredEnsemble();
            case ByPayload -> setup.getInitialRecordsWithMatchingSuffix();
            case ByEnsembleAndPayload -> setup.getInitialRecordsInDesiredEnsembleWithMatchingSuffix();
            default -> throw new IllegalArgumentException("Unhandled pred " + pred);
        };
        // the initial result set does NOT CHANGE upon second read!
        Assertions.assertThat(Stuff.sortById(result1)).isEqualTo(Stuff.sortById(expected));
        Assertions.assertThat(Stuff.sortById(result2)).isEqualTo(Stuff.sortById(expected));
    }

    // ---

    private void finallyExpectWhatTheModifierDid(@NotNull Op op) {
        final List<Stuff> actualStuff = db.readAll();
        final List<Stuff> expectedStuff = computeWhatTheModifierDid(op);
        Assertions.assertThat(Stuff.sortById(actualStuff)).isEqualTo(Stuff.sortById(expectedStuff));
    }

    // ---

    private List<Stuff> computeWhatTheModifierDid(@NotNull Op op) {
        List<Stuff> res;
        switch (op) {
            case UpdateIntoPredicateSet -> {
                // "setup.updateForMovingIn" was updated
                res = setup.getInitialRecords_RemoveRecord(setup.updateForMovingIn);
                res.add(setup.updateForMovingInChanged);
            }
            case UpdateOutOfPredicateSet -> {
                // "setup.updateForMovingOut" was updated
                res = setup.getInitialRecords_RemoveRecord(setup.updateForMovingOut);
                res.add(setup.updateForMovingOutChanged);
            }
            case Insert -> {
                // "setup.insertMe" was inserted
                res = setup.getInitialRecords_AddRecord(setup.insertMe);
            }
            case Delete -> {
                // "setup.deleteMe" was deleted
                res = setup.getInitialRecords_RemoveRecord(setup.deleteMe);
            }
            default -> throw new IllegalArgumentException("Unhandled op " + op);
        }
        res = Stuff.sortById(res); // not really needed
        return res;
    }

    // ---

    private static Stream<Arguments> provideTestArgStream() {
        // ...normally, one would expect unsoundness at these levels
        // Set<Isol> unsoundIsols = new HashSet<>(List.of(Isol.READ_UNCOMMITTED, Isol.READ_COMMITTED, Isol.REPEATABLE_READ));
        // ... but n H2, Phantom Reads already disappear at isolation level READ_COMMITTED. Thus:
        Set<Isol> unsoundIsols = new HashSet<>(List.of(Isol.READ_UNCOMMITTED, Isol.READ_COMMITTED));
        List<Arguments> res = new ArrayList<>();
        for (Isol isol : List.of(Isol.READ_UNCOMMITTED, Isol.READ_COMMITTED, Isol.REPEATABLE_READ, Isol.SERIALIZABLE, Isol.SNAPSHOT)) {
            for (Op op : List.of(Op.Insert, Op.Delete, Op.UpdateIntoPredicateSet, Op.UpdateOutOfPredicateSet)) {
                for (PhantomicPredicate pred : List.of(PhantomicPredicate.ByEnsemble, PhantomicPredicate.ByPayload, PhantomicPredicate.ByEnsembleAndPayload)) {
                    Expected expected = unsoundIsols.contains(isol) ? Expected.PhantomRead : Expected.Soundness;
                    res.add(Arguments.of(isol, op, pred, expected));
                }
            }
        }
        return res.stream();
    }

}