package name.heavycarbon.h2_exercises.transactions;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.PrintException;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import name.heavycarbon.h2_exercises.transactions.db.SessionManip;
import name.heavycarbon.h2_exercises.transactions.deadlock.AgentContainer;
import name.heavycarbon.h2_exercises.transactions.deadlock.Config;
import name.heavycarbon.h2_exercises.transactions.deadlock.Config.AlfaUpdateTiming;
import name.heavycarbon.h2_exercises.transactions.deadlock.Config.BravoFirstOp;
import name.heavycarbon.h2_exercises.transactions.deadlock.DbConfig;
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

@Slf4j
@AutoConfigureJdbc
@SpringBootTest(classes = {Db.class, SessionManip.class, TransactionalGateway.class})
public class TestElicitingDeadlock {

    private enum Expected {SmoothSailing, DeadlockException, TimeoutException}

    // Spring does its autowiring magic here.

    @Autowired
    private Db db;

    @Autowired
    private TransactionalGateway txGw;

    private final DbConfig dbConfig = new DbConfig();

    // Information on how to set up the database and what to modify

    private void setupDb(@NotNull BravoFirstOp bravoFirstOp) {
        db.rejuvenateSchema();
        db.createStuffTable();
        db.insert(dbConfig.stuff_x);
        if (bravoFirstOp.rowName.equalsIgnoreCase("k")) {
            // we will be accessing "k" in the "alternate table"
            db.createAlternateStuffTable();
            if (bravoFirstOp.rowInitiallyExists) {
                db.insertAlternate(dbConfig.stuff_k);
            }
        }
        if (bravoFirstOp.rowName.equalsIgnoreCase("z")) {
            // we will be accessing "z" in the "primary table"
            if (bravoFirstOp.rowInitiallyExists) {
                db.insert(dbConfig.stuff_z);
            }
        }
    }

    private static Expected whatToExpect(Isol isol, BravoFirstOp bravoFirstOp) {
        if (bravoFirstOp == BravoFirstOp.UpdateX) {
            // Case of "AlfaUpdateTiming.Late":
            // BRAVO updates X in step 2, and ALFA tries to update X in step 3
            // ALFA will not be able to acquire a lock to X in step 3 and
            // terminates with a "timeout exception".
            // Case of "AlfaUpdateTiming.Early":
            // ALFA updates X in step 1, and Bravo tries to update X in step 2
            // BRAVO will not be able to acquire a lock to X in step 2 and
            // terminate with a "timeout exception".
            // This behaviour occurs in all isolation levels. As expected.
            return Expected.TimeoutException;
        } else if (isol == Isol.READ_UNCOMMITTED || isol == Isol.READ_COMMITTED) {
            // Isolation levels ANSI "READ (even) UNCOMMITTED" and
            // ANSI "READ (only) COMMITTED" see no problem.
            // The transaction committing last (i.e. BRAVO) wins the update
            // race, and BRAVO's action in step 2 is of no importance,
            return Expected.SmoothSailing;
        } else if (isol == Isol.REPEATABLE_READ) {
            // In this isolation level, Bravo gets a "deadlock exception" if reads X in
            // step 2 (updating X yields a "lock timeout exception" for Alfa instead, see above).
            // Bravo also gets a "deadlock exception" if it touches an unrelated, existing record
            // Z in the same table. Inserting a new Z is ok.
            // Touching an unrelated record K in another table is ok, too.
            final var deadlockingOps =
                    List.of(
                            BravoFirstOp.ReadX,
                            BravoFirstOp.ReadZ, BravoFirstOp.UpdateZ, BravoFirstOp.DeleteZ
                    );
            if (deadlockingOps.contains(bravoFirstOp)) {
                return Expected.DeadlockException;
            } else {
                return Expected.SmoothSailing;
            }
        } else if (isol == Isol.SERIALIZABLE || isol == Isol.SNAPSHOT) {
            // In isolation levels SERIALIZABLE and SNAPSHOT, the database is very picky!
            // Any operation at all, be in on an unrelated record Z in the same table or an
            // unrelated record K in an unrelated table, causes Bravo to get a "deadlock exception".
            final var deadlockingOps = List.of(BravoFirstOp.ReadX,
                    BravoFirstOp.ReadZ, BravoFirstOp.UpdateZ, BravoFirstOp.DeleteZ, BravoFirstOp.InsertZ,
                    BravoFirstOp.ReadK, BravoFirstOp.UpdateK, BravoFirstOp.DeleteK, BravoFirstOp.InsertK);
            if (deadlockingOps.contains(bravoFirstOp)) {
                return Expected.DeadlockException;
            } else if (bravoFirstOp == BravoFirstOp.None) {
                return Expected.SmoothSailing;
            } else {
                throw new IllegalArgumentException("Everything ends in deadlock");
            }
        } else {
            throw new IllegalArgumentException("Incomplete code");
        }
    }

    // ---
    // JUnit gets the arguments for the test method from this
    // ---

    private static Stream<Arguments> provideTestArgStream() {
        List<Arguments> res = new ArrayList<>();
        for (int i = 0; i < rounds; i++) {
            for (Isol isol : Isol.values()) {
                for (AlfaUpdateTiming alfaUpdateTiming : AlfaUpdateTiming.values()) {
                    for (BravoFirstOp bravoFirstOp : BravoFirstOp.values()) {
                        final Expected expected = whatToExpect(isol, bravoFirstOp);
                        res.add(Arguments.of(
                                new Config(
                                        isol,
                                        alfaUpdateTiming,
                                        bravoFirstOp,
                                        Config.RandomStartupDelay.Yes,
                                        PrintException.No
                                ), expected));
                    }
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
    void testDeadlock(@NotNull Config config, Expected expected) {
        setupDb(config.getBravoFirstOp());
        final var ac = new AgentContainer(db, txGw, config, dbConfig);
        {
            ac.startAll();
            ac.joinAll();
        }
        if (expected == Expected.SmoothSailing) {
            assertSmoothSailing(ac);
        } else if (expected == Expected.TimeoutException) {
            assertTimeoutException(ac, config.getAlfaUpdateTiming());
        } else {
            assert expected == Expected.DeadlockException;
            assertDeadlockException(ac);
        }
    }

    private void assertSmoothSailing(@NotNull AgentContainer ac) {
        Assertions.assertThat(ac.isAnyAgentTerminatedBadly()).isFalse();
        // Bravo (the transaction that updates last) won writing X
        Assertions.assertThat(dbConfig.readX(db)).isEqualTo(dbConfig.updateString_bravo_did_x);
    }

    private void assertTimeoutException(@NotNull AgentContainer ac, @NotNull AlfaUpdateTiming aut) {
        Assertions.assertThat(ac.isAnyAgentTerminatedBadly()).isTrue();
        if (aut == AlfaUpdateTiming.Late) {
            assertIsTimeoutException(ac.getAlfa().getExceptionSeen());
            // Bravo (the transaction that updates first) won writing X and committed
            // (but could not update later because the test broke off w/o rollback)
            Assertions.assertThat(dbConfig.readX(db)).isEqualTo(dbConfig.updateString_bravo_did_x_early);
        } else {
            assertIsTimeoutException(ac.getBravo().getExceptionSeen());
            // Alfa (the transaction that updates first) won writing X and committed
            // (but could not update later because the test broke off w/o rollback)
            Assertions.assertThat(dbConfig.readX(db)).isEqualTo(dbConfig.updateString_alfa_did_x);
        }
    }

    private void assertDeadlockException(@NotNull AgentContainer ac) {
        Assertions.assertThat(ac.isAnyAgentTerminatedBadly()).isTrue();
        assertIsLockException(ac.getBravo().getExceptionSeen());
        // Alfa (the transaction that commits first) won writing X
        Assertions.assertThat(dbConfig.readX(db)).isEqualTo(dbConfig.updateString_alfa_did_x);
    }

    private void assertIsLockException(Exception ex) {
        // "org.h2.jdbc.JdbcSQLTransactionRollbackException: Deadlock detected"
        Assertions.assertThat(ex).isInstanceOf(org.springframework.dao.CannotAcquireLockException.class);
    }

    private void assertIsTimeoutException(Exception ex) {
        // "org.h2.jdbc.JdbcSQLTimeoutException: Timeout trying to lock table "STUFF""
        Assertions.assertThat(ex).isInstanceOf(org.springframework.dao.QueryTimeoutException.class);
        // Assertions.assertThat(ex).hasCauseInstanceOf(org.h2.jdbc.JdbcSQLTimeoutException.class);
    }
}
