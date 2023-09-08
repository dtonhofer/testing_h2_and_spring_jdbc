package name.heavycarbon.h2_exercises.transactions;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.PrintException;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import name.heavycarbon.h2_exercises.transactions.db.SessionManip;
import name.heavycarbon.h2_exercises.transactions.sql_timeout.AgentContainer_SqlTimeout;
import name.heavycarbon.h2_exercises.transactions.sql_timeout.Config;
import name.heavycarbon.h2_exercises.transactions.sql_timeout.DbConfig;
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
public class TestElicitingSqlTimeout {

    // Spring does its autowiring magic here.

    @Autowired
    private Db db;

    @Autowired
    private TransactionalGateway txGw;

    // Information on how to set up the database and what to modify

    private final DbConfig dbConfig = new DbConfig();

    private void setupDb() {
        db.rejuvenateSchema();
        db.createStuffTable();
        db.insert(dbConfig.stuff_a);
        db.insert(dbConfig.stuff_b);
        db.insert(dbConfig.stuff_x);
    }

    // ---
    // JUnit gets the arguments for the test method from this
    // ---

    private static Stream<Arguments> provideTestArgStream() {
        List<Arguments> res = new ArrayList<>();
        for (int i = 0; i < rounds; i++) {
            for (Isol isol : Isol.values()) {
                res.add(Arguments.of(isol));
            }
        }
        return res.stream();
    }

    // ---
    // Increase this for running the test more than once in a loop.
    // Note that these tests take a bit as they are ended by a timeout.
    // ---

    private final static int rounds = 10;

    // ---
    // The test method to call. As the annotation says, it gets its arguments from
    // method "provideTestArgStream".
    // ---

    @ParameterizedTest
    @MethodSource("provideTestArgStream")
    void testSqlTimeoutException(@NotNull Isol isol) {
        setupDb();
        final var config = new Config(isol, PrintException.No);
        final var ac = new AgentContainer_SqlTimeout(db, txGw, config, dbConfig);
        {
            ac.startAll();
            ac.joinAll();
        }
        if (isol == Isol.READ_UNCOMMITTED) {
            // Bravo read the X as updated by Alfa
            Assertions.assertThat(ac.getBravo().getReadInState3()).isEqualTo(dbConfig.stuff_x.with(dbConfig.updateString_alfa_did_x));
        } else {
            // Bravo read the X that existed of the start of transaction
            Assertions.assertThat(ac.getBravo().getReadInState3()).isEqualTo(dbConfig.stuff_x);
        }
        Assertions.assertThat(ac.isAnyAgentTerminatedBadly()).isTrue();
        // Alfa wrote its marker A
        // Assertions.assertThat(db.readById(stuff_a.getId()).orElseThrow().getPayload()).isEqualTo("ALFA WAS HERE");
        // Bravo was rolled back, so its marker B is untouched
        // Assertions.assertThat(db.readById(stuff_b.getId()).orElseThrow().getPayload()).isEqualTo("---");
        // Alfa won writing X
        Assertions.assertThat(db.readById(dbConfig.stuff_x.getId()).orElseThrow().getPayload()).isEqualTo(dbConfig.updateString_alfa_did_x);
        // We get the same exception in all isolation levels. Good!
        // Found an "org.springframework.dao.QueryTimeoutException" at the Spring Data JDBC level
        // TODO : Which is however transformed into an "org.springframework.transaction.TransactionSystemException"
        // TODO: at the @Transaction level due to inability of Spring to "translate" "org.h2.jdbc.JdbcSQLTimeoutException"
        // TODO: Can one fix that?
        assertIsTimeoutException(ac.getBravo().getExceptionSeen());
    }

    private void assertIsTimeoutException(Exception ex) {
        Assertions.assertThat(ex).isInstanceOf(org.springframework.dao.QueryTimeoutException.class);
    }
}
